package dev.hogwai.platform.cli;

import dev.hogwai.platform.host.api.HostAdapter;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.HostConfiguration;
import dev.hogwai.platform.host.api.HostException;
import dev.hogwai.platform.host.helidon.HelidonHostAdapter;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** The sole standard process bootstrap for Heron applications. */
@Command(name = "heron", subcommands = StartCommand.class, description = "Heron application launcher")
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class HeronLauncher implements Runnable {

    private static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Creates the picocli root command. */
    public HeronLauncher() {
        // populated by picocli
    }

    @Spec
    private CommandSpec commandSpec;

    /**
     * Starts the standard Heron command line application.
     *
     * @param arguments arguments supplied after the executable name
     */
    public static void main(String[] arguments) {
        int status = run(arguments);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Parses and executes the standard Heron command line.
     *
     * @param arguments arguments supplied after the executable name
     * @return zero on successful shutdown, non-zero otherwise
     */
    public static int run(String[] arguments) {
        picocli.CommandLine commandLine = new picocli.CommandLine(new HeronLauncher());
        commandLine.setParameterExceptionHandler((failure, args) -> {
            commandLine.getErr().println("heron: " + failure.getMessage());
            return 2;
        });
        commandLine.setExecutionExceptionHandler((failure, ignored, args) -> {
            commandLine.getErr().println("heron: command failed");
            return 1;
        });
        return commandLine.execute(arguments);
    }

    @Override
    public void run() {
        throw new ParameterException(commandSpec.commandLine(), "command 'start' is required");
    }

    static int run(StartCommand command, ApplicationLoaderFunction loader,
            HostAdapterFactory adapterFactory, ShutdownWaiter shutdownWaiter) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        Objects.requireNonNull(adapterFactory, "adapterFactory must not be null");
        Objects.requireNonNull(shutdownWaiter, "shutdownWaiter must not be null");

        HostApplication application = null;
        HostAdapter host = null;
        Exception primaryFailure = null;
        try {
            try (InputStream input = Files.newInputStream(command.configuration())) {
                application = loader.load(input);
            }
            host = Objects.requireNonNull(adapterFactory.create(), "host adapter must not be null");
            HostConfiguration configuration = new HostConfiguration(DEFAULT_BIND_ADDRESS, command.port(),
                    DEFAULT_REQUEST_TIMEOUT);
            host.start(application, configuration);
            shutdownWaiter.await();
        } catch (Exception failure) {
            primaryFailure = failure;
            report(safeMessage(failure));
        } finally {
            primaryFailure = stopHost(host, primaryFailure);
            primaryFailure = closeHost(host, primaryFailure);
            primaryFailure = closeApplication(application, primaryFailure);
        }
        return primaryFailure == null ? 0 : 1;
    }

    static HostAdapter createHostAdapter() {
        return new HelidonHostAdapter();
    }

    private static Exception stopHost(HostAdapter host, Exception primaryFailure) {
        if (host == null) {
            return primaryFailure;
        }
        try {
            host.stop();
        } catch (HostException | RuntimeException failure) {
            return retainPrimary(primaryFailure, failure);
        }
        return primaryFailure;
    }

    private static Exception closeHost(HostAdapter host, Exception primaryFailure) {
        if (host == null) {
            return primaryFailure;
        }
        try {
            host.close();
        } catch (RuntimeException failure) {
            return retainPrimary(primaryFailure, failure);
        }
        return primaryFailure;
    }

    private static Exception closeApplication(HostApplication application, Exception primaryFailure) {
        if (application == null) {
            return primaryFailure;
        }
        try {
            application.close();
        } catch (RuntimeException failure) {
            return retainPrimary(primaryFailure, failure);
        }
        return primaryFailure;
    }

    private static Exception retainPrimary(Exception primaryFailure, Exception cleanupFailure) {
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
            return primaryFailure;
        }
        report(safeMessage(cleanupFailure));
        return cleanupFailure;
    }

    static void awaitShutdown() throws InterruptedException {
        CountDownLatch shutdown = new CountDownLatch(1);
        Thread hook = new Thread(shutdown::countDown, "heron-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);
        shutdown.await();
    }

    private static String safeMessage(Exception failure) {
        if (failure instanceof HostException) {
            return failure.getMessage();
        }
        return "operation failed";
    }

    private static void report(String message) {
        System.err.println(message == null || message.isBlank() ? "heron: operation failed" : "heron: " + message);
    }

    @FunctionalInterface
    interface ApplicationLoaderFunction {
        HostApplication load(InputStream input) throws Exception;
    }

    @FunctionalInterface
    interface HostAdapterFactory {
        HostAdapter create();
    }

    @FunctionalInterface
    interface ShutdownWaiter {
        void await() throws InterruptedException;
    }
}
