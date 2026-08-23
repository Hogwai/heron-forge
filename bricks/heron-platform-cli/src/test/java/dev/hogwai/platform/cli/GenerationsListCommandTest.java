package dev.hogwai.platform.cli;

import java.nio.file.Path;
import java.time.Instant;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link GenerationsListCommand}. */
class GenerationsListCommandTest {

    private static final Instant BASE = Instant.parse("2026-06-01T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void listsGenerationsMostRecentFirst() {
        Path storeRoot = temporaryDirectory.resolve("store");
        String newest = "gggg";
        String middle = "mmmm";
        String oldest = "aaaa";
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, oldest, GenerationStatus.STABLE, BASE, yamlFor(oldest));
            RegistryCliTestSupport.save(store, newest, GenerationStatus.EXPERIMENTAL, BASE.plusSeconds(20),
                    yamlFor(newest));
            RegistryCliTestSupport.save(store, middle, GenerationStatus.STABLE, BASE.plusSeconds(10),
                    yamlFor(middle));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new GenerationsListCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION);

        assertThat(execution.status()).isZero();
        assertThat(execution.out()).containsSubsequence(newest, middle, oldest);
        assertThat(execution.out()).contains("STABLE").contains("EXPERIMENTAL")
                .contains("2026-06-01T00:00:10Z").contains("tester");
    }

    @Test
    void otherApplicationsAreNotListed() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "aaaa", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new GenerationsListCommand()),
                "--store", storeRoot.toString(), "--app", "other-app");

        assertThat(execution.status()).isZero();
        assertThat(execution.out()).isBlank();
    }

    private static String yamlFor(String generationId) {
        // Distinct content per fabricated generationRecord so hashes never collide.
        return RegistryCliTestSupport.yaml(generationId);
    }
}
