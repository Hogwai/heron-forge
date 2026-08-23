package dev.hogwai.platform.cli;

import java.nio.file.Path;
import java.time.Instant;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link GenerationsMarkCommand}. */
class GenerationsMarkCommandTest {

    private static final Instant BASE = Instant.parse("2026-06-01T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void movesGenerationForwardAndPrintsOldAndNewStatus() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "aaaa", GenerationStatus.EXPERIMENTAL, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new GenerationsMarkCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--generation", "aaaa", "--status", "stable");

        assertThat(execution.status()).isZero();
        assertThat(execution.out()).contains("EXPERIMENTAL").contains("STABLE");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            assertThat(store.find(RegistryCliTestSupport.APPLICATION, "aaaa"))
                    .hasValueSatisfying(generationRecord ->
                            assertThat(generationRecord.status()).isEqualTo(GenerationStatus.STABLE));
        }
    }

    @Test
    void parsesStatusCaseInsensitively() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "aaaa", GenerationStatus.EXPERIMENTAL, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new GenerationsMarkCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--generation", "aaaa", "--status", "STABLE");

        assertThat(execution.status()).isZero();
    }

    @Test
    void rejectsUnknownStatusWithClearError() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "aaaa", GenerationStatus.EXPERIMENTAL, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new GenerationsMarkCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--generation", "aaaa", "--status", "published");

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("unknown status 'published'");
    }

    @Test
    void rejectsUnknownGeneration() {
        Path storeRoot = temporaryDirectory.resolve("store");

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new GenerationsMarkCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--generation", "missing", "--status", "stable");

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("no generation 'missing'");
    }

    @Test
    void rejectsBackwardSameStatusAndTerminalRetiredTransitions() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "aaaa", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution backward = mark(storeRoot, "experimental");
        RegistryCliTestSupport.Execution same = mark(storeRoot, "stable");

        assertThat(backward.status()).isEqualTo(1);
        assertThat(backward.err()).contains("strictly forward");
        assertThat(same.status()).isEqualTo(1);
        assertThat(same.err()).contains("cannot move generation 'aaaa' from STABLE to STABLE");
    }

    @Test
    void retiredIsTerminal() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "aaaa", GenerationStatus.RETIRED, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = mark(storeRoot, "retired");

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("from RETIRED to RETIRED");
    }

    private RegistryCliTestSupport.Execution mark(Path storeRoot, String status) {
        return RegistryCliTestSupport.execute(new CommandLine(new GenerationsMarkCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--generation", "aaaa", "--status", status);
    }
}
