package dev.hogwai.platform.cli;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.registry.GenerationDiff;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Picocli subcommand comparing two stored generations of an application.
 *
 * <p>Both records are loaded from the store and compared structurally by {@link GenerationDiff}.
 * Finding differences is a successful outcome (exit code 0), only business errors (unknown generation, unreadable store, invalid YAML exit with 1).
 */
@Command(name = "diff",
        description = "Compare two stored generations of an application")
public final class GenerationsDiffCommand implements Callable<Integer> {

    @Option(names = "--store", defaultValue = StartCommand.DEFAULT_STORE,
            description = "generation store root directory (default: ./" + StartCommand.DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--app", required = true, description = "application id in the generation store")
    private String applicationId;

    @Option(names = "--from", required = true, description = "reference generation id")
    private String fromGenerationId;

    @Option(names = "--to", required = true, description = "compared generation id")
    private String toGenerationId;

    @Spec
    private CommandSpec commandSpec;

    /** Creates the picocli generations diff command. */
    public GenerationsDiffCommand() {
        // populated by picocli
    }

    @Override
    public Integer call() {
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            Optional<GenerationRecord> from = generationStore.find(applicationId, fromGenerationId);
            if (from.isEmpty()) {
                commandSpec.commandLine().getErr().println(
                        "heron: no generation '%s' for application '%s'".formatted(fromGenerationId,
                                applicationId));
                return 1;
            }
            Optional<GenerationRecord> to = generationStore.find(applicationId, toGenerationId);
            if (to.isEmpty()) {
                commandSpec.commandLine().getErr().println(
                        "heron: no generation '%s' for application '%s'".formatted(toGenerationId,
                                applicationId));
                return 1;
            }
            GenerationDiff.DiffResult result = GenerationDiff.diff(from.get(), to.get());
            commandSpec.commandLine().getOut().println(result.render());
            return 0;
        } catch (PlatformException | UncheckedIOException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return 1;
        }
    }
}
