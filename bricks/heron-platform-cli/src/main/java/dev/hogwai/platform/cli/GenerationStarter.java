package dev.hogwai.platform.cli;

import java.nio.file.Path;
import java.time.Clock;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.execution.RuntimeApplication;
import dev.hogwai.platform.runtime.registry.GenerationActivator;
import dev.hogwai.platform.spi.error.PlatformException;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Starts an application from a stored generation: applies the
 * {@link GenerationSelection} policy, activates the selected generationRecord through
 * {@link GenerationActivator} and serves it via the standard host wiring.
 */
final class GenerationStarter {

    private final Path store;
    private final String applicationId;
    private final String generationId;
    private final int port;
    private final CommandSpec commandSpec;

    /**
     * Creates a starter for one invocation of {@code heron start --store}.
     *
     * @param store        the generation store root directory
     * @param applicationId the application id in the store
     * @param generationId the explicitly requested generation id, or {@code null}
     * @param port         the HTTP bind port
     * @param commandSpec  the picocli spec used for user-facing output
     */
    GenerationStarter(Path store, String applicationId, String generationId, int port,
            CommandSpec commandSpec) {
        this.store = store;
        this.applicationId = applicationId;
        this.generationId = generationId;
        this.port = port;
        this.commandSpec = commandSpec;
    }

    /**
     * Runs the selection and activation flow.
     *
     * @return zero on clean shutdown, non-zero on any failure
     */
    Integer start() {
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            GenerationSelection.Selected selection = select(generationStore);
            if (selection == null) {
                return 1;
            }
            return activate(selection);
        } catch (PlatformException failure) {
            commandSpec.commandLine().getErr().println("heron: activation failed: " + failure.getMessage());
            return 1;
        }
    }

    private GenerationSelection.Selected select(FileGenerationStore generationStore) {
        try {
            return GenerationSelection.select(generationStore.history(applicationId), generationId);
        } catch (IllegalArgumentException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return null;
        }
    }

    private Integer activate(GenerationSelection.Selected selection) {
        if (selection.warning() != null) {
            commandSpec.commandLine().getErr().println("heron: warning: %s".formatted(selection.warning()));
        }
        try (RuntimeApplication application =
                     new GenerationActivator(Clock.systemUTC()).activate(selection.generationRecord())) {
            return HeronLauncher.run(port, application,
                    HeronLauncher::createHostAdapter, HeronLauncher::awaitShutdown);
        }
    }
}
