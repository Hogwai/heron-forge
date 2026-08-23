package dev.hogwai.platform.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Callable;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.registry.RegistrationResult;
import dev.hogwai.platform.runtime.registry.RegistryService;
import dev.hogwai.platform.spi.error.PlatformException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Picocli subcommand that seals and stores one application generation. */
@Command(name = "register",
        description = "Validate, seal (SHA-256) and store a generation of an application")
public final class RegisterCommand implements Callable<Integer> {

    @Option(names = "--config", required = true, converter = ReadablePathConverter.class,
            description = "readable YAML application configuration")
    private Path configuration;

    @Option(names = "--store", defaultValue = StartCommand.DEFAULT_STORE,
            description = "generation store root directory (default: ./" + StartCommand.DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--created-by", defaultValue = "heron-cli",
            description = "attribution recorded with the generation (default: heron-cli)")
    private String createdBy;

    @Spec
    private CommandSpec commandSpec;

    /** Creates the picocli register command. */
    public RegisterCommand() {
        // populated by picocli
    }

    @Override
    public Integer call() throws IOException {
        String rawYaml = Files.readString(configuration, StandardCharsets.UTF_8);
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            RegistrationResult result = new RegistryService(generationStore, Clock.systemUTC())
                    .register(rawYaml, createdBy);
            report(result);
            return 0;
        } catch (PlatformException | UncheckedIOException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return 1;
        }
    }

    private void report(RegistrationResult result) {
        String template = result.created()
                ? "registered application '%s' generation '%s' status %s"
                : "already registered (unchanged): application '%s' generation '%s' status %s";
        commandSpec.commandLine().getOut().println(template.formatted(
                result.generationRecord().applicationId(), result.generationRecord().generationId(), result.generationRecord().status()));
    }
}
