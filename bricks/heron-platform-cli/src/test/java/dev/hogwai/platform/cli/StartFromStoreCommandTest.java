package dev.hogwai.platform.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.hogwai.platform.registry.FileGenerationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Command-level tests for {@code heron start --store}: only the refusal paths
 * are exercised end to end so no test ever binds a real port; the acceptance
 * paths of the policy are covered unit-wise by {@link GenerationSelectionTest}.
 */
class StartFromStoreCommandTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresEitherConfigOrApp() {
        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new HeronLauncher()), "start");

        assertThat(execution.status()).isEqualTo(2);
        assertThat(execution.err()).contains("either --config or --app is required");
    }

    @Test
    void configAndAppAreMutuallyExclusive() throws IOException {
        Path configuration = configurationFile();

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new HeronLauncher()), "start",
                "--config", configuration.toString(), "--app", RegistryCliTestSupport.APPLICATION);

        assertThat(execution.status()).isEqualTo(2);
        assertThat(execution.err()).contains("mutually exclusive");
    }

    @Test
    void explicitRetiredGenerationIsRefusedBeforeActivation() {
        Path storeRoot = temporaryDirectory.resolve("store");
        String generationId = RegistryCliTestSupport.register(storeRoot, RegistryCliTestSupport.yaml("host-1"));
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            store.transition(RegistryCliTestSupport.APPLICATION, generationId,
                    dev.hogwai.platform.spi.registry.GenerationStatus.STABLE);
            store.transition(RegistryCliTestSupport.APPLICATION, generationId,
                    dev.hogwai.platform.spi.registry.GenerationStatus.DEPRECATED);
            store.transition(RegistryCliTestSupport.APPLICATION, generationId,
                    dev.hogwai.platform.spi.registry.GenerationStatus.RETIRED);
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new HeronLauncher()), "start",
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--generation", generationId);

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("RETIRED");
    }

    @Test
    void deprecatedGenerationIsRefusedByDefaultWhenNoStableExists() {
        Path storeRoot = temporaryDirectory.resolve("store");
        String generationId = RegistryCliTestSupport.register(storeRoot, RegistryCliTestSupport.yaml("host-1"));
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            store.transition(RegistryCliTestSupport.APPLICATION, generationId,
                    dev.hogwai.platform.spi.registry.GenerationStatus.STABLE);
            store.transition(RegistryCliTestSupport.APPLICATION, generationId,
                    dev.hogwai.platform.spi.registry.GenerationStatus.DEPRECATED);
        }

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new HeronLauncher()), "start",
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION);

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("no STABLE generation");
    }

    @Test
    void unknownApplicationIsRefused() {
        Path storeRoot = temporaryDirectory.resolve("store");

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new HeronLauncher()), "start",
                "--store", storeRoot.toString(), "--app", "unknown-app");

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("no STABLE generation");
    }

    private Path configurationFile() throws IOException {
        Path configuration = temporaryDirectory.resolve("app.yaml");
        Files.writeString(configuration, RegistryCliTestSupport.yaml("localhost"));
        return configuration;
    }
}
