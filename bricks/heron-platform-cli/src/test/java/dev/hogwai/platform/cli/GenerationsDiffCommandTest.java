package dev.hogwai.platform.cli;

import java.nio.file.Path;
import java.time.Instant;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GenerationsDiffCommand}.
 */
class GenerationsDiffCommandTest {

    private static final Instant BASE = Instant.parse("2026-06-01T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void printsStructuredDifferencesBetweenTwoGenerations() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "from-gen", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
            RegistryCliTestSupport.save(store, "to-gen", GenerationStatus.EXPERIMENTAL,
                    BASE.plusSeconds(10), RegistryCliTestSupport.yaml("remote"));
        }

        RegistryCliTestSupport.Execution execution = diff(storeRoot, "from-gen", "to-gen");

        assertThat(execution.status()).isZero();
        assertThat(execution.out())
                .contains("~ capability.config 'source'")
                .contains("'host': localhost -> remote")
                .doesNotContain("no differences");
    }

    @Test
    void identicalContentRendersNoDifferencesWithSuccessExitCode() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "gen-1", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
            RegistryCliTestSupport.save(store, "gen-2", GenerationStatus.EXPERIMENTAL,
                    BASE.plusSeconds(10), RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = diff(storeRoot, "gen-1", "gen-2");

        assertThat(execution.status()).isZero();
        assertThat(execution.out()).contains("no differences");
    }

    @Test
    void rejectsUnknownFromOrToGeneration() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "from-gen", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution missingTo = diff(storeRoot, "from-gen", "missing");
        RegistryCliTestSupport.Execution missingFrom = diff(storeRoot, "missing", "from-gen");

        assertThat(missingTo.status()).isEqualTo(1);
        assertThat(missingTo.err()).contains("no generation 'missing'");
        assertThat(missingFrom.status()).isEqualTo(1);
        assertThat(missingFrom.err()).contains("no generation 'missing'");
    }

    @Test
    void invalidStoredYamlFailsCleanly() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "broken", GenerationStatus.EXPERIMENTAL, BASE,
                    "this is: [not: valid yaml");
            RegistryCliTestSupport.save(store, "valid", GenerationStatus.EXPERIMENTAL,
                    BASE.plusSeconds(10), RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = diff(storeRoot, "broken", "valid");

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).isNotBlank();
    }

    private RegistryCliTestSupport.Execution diff(Path storeRoot, String from, String to) {
        return RegistryCliTestSupport.execute(new CommandLine(new GenerationsDiffCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--from", from, "--to", to);
    }
}
