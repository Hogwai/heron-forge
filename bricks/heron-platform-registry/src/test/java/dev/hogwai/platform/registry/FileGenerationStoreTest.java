package dev.hogwai.platform.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Roundtrip, idempotence, lifecycle, persistence, secret-literal and
 * corruption behavior of {@link FileGenerationStore} against a temporary
 * directory.
 */
class FileGenerationStoreTest {

    private static final String APPLICATION = "supply-chain-demo";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void roundtripsRecordThroughDisk() throws IOException {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        GenerationRecord generationRecord = generateRecord("gen-1", GenerationStatus.EXPERIMENTAL, T0);

        store.save(generationRecord);

        assertThat(store.find(APPLICATION, "gen-1")).contains(generationRecord);
        Path config = tempDir.resolve(APPLICATION).resolve("gen-1").resolve("config.yaml");
        assertThat(config).exists();
        assertThat(Files.readString(config)).isEqualTo(generationRecord.rawYaml());
    }

    @Test
    void sortsHistoryByDescendingCreatedAtWithGenerationIdTieBreak() {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        GenerationRecord newest = generateRecord("n-gen", GenerationStatus.STABLE, T0.plusSeconds(10));
        GenerationRecord tieLow = generateRecord("a-gen", GenerationStatus.EXPERIMENTAL, T0);
        GenerationRecord tieMiddle = generateRecord("b-gen", GenerationStatus.EXPERIMENTAL, T0);
        GenerationRecord tieHigh = generateRecord("z-gen", GenerationStatus.EXPERIMENTAL, T0);
        store.save(tieHigh);
        store.save(newest);
        store.save(tieMiddle);
        store.save(tieLow);

        List<GenerationRecord> history = store.history(APPLICATION);

        assertThat(history).extracting(GenerationRecord::generationId)
                .containsExactly("n-gen", "a-gen", "b-gen", "z-gen");
    }

    @Test
    void saveIsIdempotentPerApplicationAndGenerationPair() {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        store.save(generateRecord("gen-1", GenerationStatus.EXPERIMENTAL, T0));
        GenerationRecord rewritten = new GenerationRecord(APPLICATION, "gen-1", "sha-256",
                "kind: rewritten\n", GenerationStatus.STABLE, T0, "someone-else");

        store.save(rewritten);

        assertThat(store.find(APPLICATION, "gen-1")).contains(rewritten);
        assertThat(store.history(APPLICATION)).hasSize(1);
    }

    @Test
    void walksFullLifecycleForwardAndPersistsStatusAcrossReopen() {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        store.save(generateRecord("gen-1", GenerationStatus.EXPERIMENTAL, T0));

        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.STABLE)).isTrue();
        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.DEPRECATED)).isTrue();
        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.RETIRED)).isTrue();

        FileGenerationStore reopened = new FileGenerationStore(tempDir);
        assertThat(reopened.find(APPLICATION, "gen-1"))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(GenerationStatus.RETIRED));
    }

    @Test
    void rejectsBackwardSameStatusAndUnknownTransitionsAndTerminalRetired() {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        store.save(generateRecord("gen-1", GenerationStatus.STABLE, T0));

        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.EXPERIMENTAL)).isFalse();
        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.STABLE)).isFalse();
        assertThat(store.transition(APPLICATION, "unknown", GenerationStatus.STABLE)).isFalse();

        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.RETIRED)).isTrue();
        assertThat(store.transition(APPLICATION, "gen-1", GenerationStatus.RETIRED)).isFalse();
        // RETIRED is terminal: nothing can follow it.
        assertThat(store.find(APPLICATION, "gen-1"))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(GenerationStatus.RETIRED));
    }

    @Test
    void transitionKeepsEveryOtherComponentUnchanged() {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        GenerationRecord original = generateRecord("gen-1", GenerationStatus.EXPERIMENTAL, T0);
        store.save(original);

        store.transition(APPLICATION, "gen-1", GenerationStatus.STABLE);

        assertThat(store.find(APPLICATION, "gen-1")).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(GenerationStatus.STABLE);
            assertThat(found.configSha256()).isEqualTo(original.configSha256());
            assertThat(found.rawYaml()).isEqualTo(original.rawYaml());
            assertThat(found.createdAt()).isEqualTo(original.createdAt());
            assertThat(found.createdBy()).isEqualTo(original.createdBy());
        });
    }

    @Test
    void storesPlaceholderLiteralsVerbatimOnDisk() throws IOException {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        String rawYaml = """
                data:
                  url: ${HERON_DB_URL}
                  password: ${HERON_DB_PASSWORD}
                """;
        store.save(new GenerationRecord(APPLICATION, "gen-secret", "sha-256", rawYaml,
                GenerationStatus.EXPERIMENTAL, T0, "test"));

        Path config = tempDir.resolve(APPLICATION).resolve("gen-secret").resolve("config.yaml");
        assertThat(Files.readString(config)).isEqualTo(rawYaml);
        assertThat(Files.readString(config)).contains("${HERON_DB_PASSWORD}");
    }

    @Test
    void ignoresCorruptedRecordEntriesInFindAndHistoryWithoutCrashing() throws IOException {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        store.save(generateRecord("gen-valid", GenerationStatus.EXPERIMENTAL, T0));
        Path corruptedDirectory = tempDir.resolve(APPLICATION).resolve("gen-corrupted");
        Files.createDirectories(corruptedDirectory);
        Files.writeString(corruptedDirectory.resolve("record.json"), "{\"applicationId\": \"trunc");
        Files.writeString(corruptedDirectory.resolve("config.yaml"), "kind: whatever\n");

        assertThat(store.find(APPLICATION, "gen-corrupted")).isEmpty();
        assertThat(store.history(APPLICATION))
                .extracting(GenerationRecord::generationId)
                .containsExactly("gen-valid");
    }

    @Test
    void closeIsIdempotent() {
        FileGenerationStore store = new FileGenerationStore(tempDir);

        assertThatCode(() -> {
            store.close();
            store.close();
        }).doesNotThrowAnyException();
    }

    private static GenerationRecord generateRecord(String generationId, GenerationStatus status, Instant createdAt) {
        return new GenerationRecord(APPLICATION, generationId, "sha256-" + generationId,
                "kind: test\nname: " + generationId + "\n", status, createdAt, "test");
    }
}
