package dev.hogwai.platform.cli;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import dev.hogwai.platform.spi.registry.GenerationStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Picocli subcommand moving a stored generation forward in its lifecycle.
 *
 * <p>The target status is parsed case-insensitively and delegated to
 * {@link GenerationStore#transition}, which enforces the strictly monotone
 * lifecycle {@code EXPERIMENTAL -> STABLE -> DEPRECATED -> RETIRED}. Unknown
 * statuses, unknown records, backward moves, same-status no-ops and moves out
 * of the terminal {@code RETIRED} status all fail with a clear message and
 * exit code 1.
 */
@Command(name = "mark",
        description = "Move a stored generation forward to a new lifecycle status")
public final class GenerationsMarkCommand implements Callable<Integer> {

    @Option(names = "--store", defaultValue = StartCommand.DEFAULT_STORE,
            description = "generation store root directory (default: ./" + StartCommand.DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--app", required = true, description = "application id in the generation store")
    private String applicationId;

    @Option(names = "--generation", required = true, description = "generation id to move forward")
    private String generationId;

    @Option(names = "--status", required = true,
            description = "target status: experimental|stable|deprecated|retired (case-insensitive)")
    private String status;

    @Spec
    private CommandSpec commandSpec;

    /**
     * Creates the picocli generations mark command.
     */
    public GenerationsMarkCommand() {
        // populated by picocli
    }

    @Override
    public Integer call() {
        GenerationStatus target = parseStatus();
        if (target == null) {
            return 1;
        }
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            Optional<GenerationRecord> current = generationStore.find(applicationId, generationId);
            if (current.isEmpty()) {
                commandSpec.commandLine().getErr().println(
                        "heron: no generation '%s' for application '%s'".formatted(generationId, applicationId));
                return 1;
            }
            GenerationStatus from = current.get().status();
            if (!generationStore.transition(applicationId, generationId, target)) {
                commandSpec.commandLine().getErr().println((
                        "heron: cannot move generation '%s' from %s to %s: transitions are strictly "
                                + "forward EXPERIMENTAL -> STABLE -> DEPRECATED -> RETIRED")
                        .formatted(generationId, from, target));
                return 1;
            }
            commandSpec.commandLine().getOut().println("generation '%s' moved from %s to %s"
                    .formatted(generationId, from, target));
            return 0;
        } catch (PlatformException | UncheckedIOException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return 1;
        }
    }

    private GenerationStatus parseStatus() {
        try {
            return GenerationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            commandSpec.commandLine().getErr().println("heron: unknown status '" + status
                    + "'; expected one of experimental, stable, deprecated, retired");
            return null;
        }
    }
}
