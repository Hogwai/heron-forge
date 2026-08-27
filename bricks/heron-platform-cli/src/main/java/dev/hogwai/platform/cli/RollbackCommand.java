package dev.hogwai.platform.cli;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Picocli subcommand resolving the rollback target of an application.
 *
 * <p>Heron is an activation-at-boot platform: there is no hot-swap yet
 * (Phase 3 of the generation registry plan).
 * Rollback resolves the previous STABLE generation
 * The most recent one is strictly older than the current latest STABLE and prints its id together with the exact
 * {@code heron start} command an operator must run to activate it.
 * Fails when fewer than two STABLE generations exist.
 */
@Command(name = "rollback",
        description = "Resolve the previous STABLE generation and print the exact heron start "
                + "command to activate it (activation happens at boot; no hot-swap)")
public final class RollbackCommand implements Callable<Integer> {

    @Option(names = "--store", defaultValue = StartCommand.DEFAULT_STORE,
            description = "generation store root directory (default: ./" + StartCommand.DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--app", required = true, description = "application id in the generation store")
    private String applicationId;

    @Spec
    private CommandSpec commandSpec;

    /**
     * Creates the picocli rollback command.
     */
    public RollbackCommand() {
        // populated by picocli
    }

    @Override
    public Integer call() {
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            Optional<GenerationRecord> target =
                    GenerationSelection.previousStable(generationStore.history(applicationId));
            if (target.isEmpty()) {
                commandSpec.commandLine().getErr().println("heron: rollback requires at least two "
                        + "STABLE generations for application '" + applicationId + "'");
                return 1;
            }
            report(target.get());
            return 0;
        } catch (PlatformException | UncheckedIOException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return 1;
        }
    }

    private void report(GenerationRecord target) {
        commandSpec.commandLine().getOut()
                .println("rollback target: generation '%s' (status %s)".formatted(
                        target.generationId(), target.status()));
        commandSpec.commandLine().getOut().println("heron start --store %s --app %s --generation %s"
                .formatted(store, applicationId, target.generationId()));
    }
}
