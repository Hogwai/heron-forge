package dev.hogwai.platform.cli;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Picocli subcommand listing the stored generations of an application.
 */
@Command(name = "list",
        description = "List the stored generations of an application, most recent first")
public final class GenerationsListCommand implements Callable<Integer> {

    @Option(names = "--store", defaultValue = StartCommand.DEFAULT_STORE,
            description = "generation store root directory (default: ./" + StartCommand.DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--app", required = true, description = "application id in the generation store")
    private String applicationId;

    @Spec
    private CommandSpec commandSpec;

    /**
     * Creates the picocli generations list command.
     */
    public GenerationsListCommand() {
        // populated by picocli
    }

    @Override
    public Integer call() {
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            List<GenerationRecord> history = generationStore.history(applicationId);
            history.forEach(this::print);
            return 0;
        } catch (PlatformException | UncheckedIOException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return 1;
        }
    }

    private void print(GenerationRecord generationRecord) {
        commandSpec.commandLine().getOut().println("%s  %s  %s  %s".formatted(
                generationRecord.generationId(), generationRecord.status(),
                generationRecord.createdAt().toString(), generationRecord.createdBy()));
    }
}
