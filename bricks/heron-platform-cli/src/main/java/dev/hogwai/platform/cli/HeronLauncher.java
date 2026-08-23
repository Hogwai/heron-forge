package dev.hogwai.platform.cli;

import dev.hogwai.platform.spi.host.HostAdapter;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.host.HostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;

/** The sole standard process bootstrap for Heron applications. */
@Command(name = "heron", mixinStandardHelpOptions = true,
        subcommands = {StartCommand.class, InitCommand.class, CreateCommand.class,
                RegisterCommand.class, GenerationsCommand.class, RollbackCommand.class},
        description = "Heron application launcher")
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class HeronLauncher implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeronLauncher.class);

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
    static void main(String[] arguments) {
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
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }

    static int run(StartCommand command, ApplicationLoaderFunction loader,
            HostAdapterFactory adapterFactory, ShutdownWaiter shutdownWaiter) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        Objects.requireNonNull(adapterFactory, "adapterFactory must not be null");
        Objects.requireNonNull(shutdownWaiter, "shutdownWaiter must not be null");

        HostApplication application;
        try {
            application = loadApplication(command, loader);
        } catch (Exception failure) {
            report(safeMessage(failure));
            Thread.currentThread().interrupt();
            return 1;
        }
        return run(command.port(), application, adapterFactory, shutdownWaiter);
    }

    static int run(int port, HostApplication application,
            HostAdapterFactory adapterFactory, ShutdownWaiter shutdownWaiter) {
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(adapterFactory, "adapterFactory must not be null");
        Objects.requireNonNull(shutdownWaiter, "shutdownWaiter must not be null");

        HostAdapter host = null;
        Exception primaryFailure = null;
        try {
            host = Objects.requireNonNull(adapterFactory.create(), "host adapter must not be null");
            HostConfiguration configuration = new HostConfiguration(DEFAULT_BIND_ADDRESS, port,
                    DEFAULT_REQUEST_TIMEOUT);
            host.start(application, configuration);
            shutdownWaiter.await();
        } catch (Exception failure) {
            primaryFailure = failure;
            report(safeMessage(failure));
            Thread.currentThread().interrupt();
        } finally {
            primaryFailure = stopHost(host, primaryFailure);
            primaryFailure = closeHost(host, primaryFailure);
            primaryFailure = closeApplication(application, primaryFailure);
        }
        return primaryFailure == null ? 0 : 1;
    }

    static HostAdapter createHostAdapter() throws HostException {
        return ServiceLoader.load(HostAdapter.class).stream()
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseThrow(() -> new HostException("No HostAdapter implementation found on the classpath"));
    }

    private static HostApplication loadApplication(StartCommand command,
            ApplicationLoaderFunction loader) throws Exception {
        try (InputStream input = Files.newInputStream(command.configuration())) {
            return loader.load(input);
        }
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
        LOGGER.error("{}", message == null || message.isBlank() ? "operation failed" : message);
    }

    @FunctionalInterface
    @SuppressWarnings("java:S112")
    interface ApplicationLoaderFunction {
        HostApplication load(InputStream input) throws Exception;
    }

    @FunctionalInterface
    interface HostAdapterFactory {
        HostAdapter create() throws HostException;
    }

    @FunctionalInterface
    interface ShutdownWaiter {
        void await() throws InterruptedException;
    }
}
