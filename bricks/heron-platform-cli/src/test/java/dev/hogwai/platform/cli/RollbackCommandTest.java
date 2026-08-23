package dev.hogwai.platform.cli;

import java.nio.file.Path;
import java.time.Instant;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link RollbackCommand}. */
class RollbackCommandTest {

    private static final Instant BASE = Instant.parse("2026-06-01T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesThePreviousStableGenerationAndPrintsTheStartCommand() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "old-stable", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("host-1"));
            RegistryCliTestSupport.save(store, "current-stable", GenerationStatus.STABLE, BASE.plusSeconds(10),
                    RegistryCliTestSupport.yaml("host-2"));
            RegistryCliTestSupport.save(store, "candidate", GenerationStatus.EXPERIMENTAL, BASE.plusSeconds(20),
                    RegistryCliTestSupport.yaml("host-3"));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new RollbackCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION);

        assertThat(execution.status()).isZero();
        assertThat(execution.out()).contains("old-stable")
                .contains("heron start --store %s --app %s --generation old-stable"
                        .formatted(storeRoot, RegistryCliTestSupport.APPLICATION));
    }

    @Test
    void failsWhenFewerThanTwoStableGenerationsExist() {
        Path storeRoot = temporaryDirectory.resolve("store");
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryCliTestSupport.save(store, "only-stable", GenerationStatus.STABLE, BASE,
                    RegistryCliTestSupport.yaml("localhost"));
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new RollbackCommand()),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION);

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("at least two STABLE");
    }

    @Test
    void failsWhenTheApplicationHasNoGeneration() {
        Path storeRoot = temporaryDirectory.resolve("store");

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new RollbackCommand()),
                "--store", storeRoot.toString(), "--app", "unknown-app");

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("at least two STABLE");
    }
}
