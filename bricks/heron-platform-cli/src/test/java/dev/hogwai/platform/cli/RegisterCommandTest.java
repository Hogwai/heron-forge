package dev.hogwai.platform.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.hogwai.platform.registry.FileGenerationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link RegisterCommand}. */
class RegisterCommandTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void registersASealedGenerationOnDisk() throws IOException {
        Path configuration = configurationFile("localhost");
        Path storeRoot = temporaryDirectory.resolve("store");

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new RegisterCommand()),
                "--config", configuration.toString(), "--store", storeRoot.toString(),
                "--created-by", "tester");

        assertThat(execution.status()).isZero();
        String generationId = RegistryCliTestSupport.sha256(RegistryCliTestSupport.yaml("localhost"));
        assertThat(execution.out()).contains("registered application 'cli-demo'")
                .contains(generationId)
                .contains("EXPERIMENTAL");
        Path generationDirectory = storeRoot.resolve("cli-demo").resolve(generationId);
        assertThat(generationDirectory.resolve("config.yaml")).exists();
        assertThat(generationDirectory.resolve("record.json")).exists();
    }

    @Test
    void registeringIdenticalYamlReportsAnUnchangedRecord() throws IOException {
        Path configuration = configurationFile("localhost");
        Path storeRoot = temporaryDirectory.resolve("store");
        CommandLine commandLine = new CommandLine(new RegisterCommand());

        RegistryCliTestSupport.execute(commandLine,
                "--config", configuration.toString(), "--store", storeRoot.toString());
        RegistryCliTestSupport.Execution second = RegistryCliTestSupport.execute(commandLine,
                "--config", configuration.toString(), "--store", storeRoot.toString());

        assertThat(second.status()).isZero();
        assertThat(second.out()).contains("already registered (unchanged)");
    }

    @Test
    void invalidYamlFailsWithoutPersistingAnything() throws IOException {
        Path configuration = temporaryDirectory.resolve("invalid.yaml");
        Files.writeString(configuration, "not: [valid");
        Path storeRoot = temporaryDirectory.resolve("store");

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new RegisterCommand()),
                "--config", configuration.toString(), "--store", storeRoot.toString());

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).isNotBlank();
        assertThat(storeRoot).doesNotExist();
    }

    @Test
    void unknownProviderFailsValidationAndPersistsNothing() throws IOException {
        Path configuration = temporaryDirectory.resolve("unknown-provider.yaml");
        Files.writeString(configuration, """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: source
                    provider:
                      id: does-not-exist
                      version: 1.0.0
                    config:
                      host: localhost
                """);
        Path storeRoot = temporaryDirectory.resolve("store");

        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(
                new CommandLine(new RegisterCommand()),
                "--config", configuration.toString(), "--store", storeRoot.toString());

        assertThat(execution.status()).isEqualTo(1);
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            assertThat(store.history("cli-demo")).isEmpty();
        }
    }

    private Path configurationFile(String host) throws IOException {
        Path configuration = temporaryDirectory.resolve("app.yaml");
        Files.writeString(configuration, RegistryCliTestSupport.yaml(host));
        return configuration;
    }
}
