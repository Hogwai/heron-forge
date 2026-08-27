package dev.hogwai.platform.cli;

import dev.hogwai.platform.runtime.load.ApplicationLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Picocli subcommand that starts the standard Heron host.
 */
@Command(name = "start",
        description = "Start the Heron host from a YAML configuration or from a stored generation")
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class StartCommand implements Callable<Integer> {

    /**
     * Default HTTP port used when no explicit port is supplied.
     */
    public static final int DEFAULT_PORT = 8080;

    /**
     * Default generation store root used when no explicit store is supplied.
     */
    public static final String DEFAULT_STORE = "registry";

    /**
     * Conventional location of the exported UI widget manifest.
     */
    public static final String DEFAULT_UI_MANIFEST = "web/ui-shell/generated/widgets.json";

    @Option(names = "--config", converter = ReadablePathConverter.class,
            description = "readable YAML application configuration (mutually exclusive with --app)")
    private Path configuration;

    @Option(names = "--store", defaultValue = DEFAULT_STORE,
            description = "generation store root directory (default: ./" + DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--app",
            description = "application id in the generation store; starts from the store "
                    + "instead of --config")
    private String applicationId;

    @Option(names = "--generation",
            description = "explicit generation id (default: latest STABLE; required to start "
                    + "a DEPRECATED generation; RETIRED generations are always refused)")
    private String generationId;

    @Option(names = "--port", defaultValue = "" + DEFAULT_PORT, converter = PortConverter.class,
            description = "HTTP bind port (0..65535)")
    private int port;

    @Option(names = "--ui-manifest", defaultValue = DEFAULT_UI_MANIFEST,
            description = "path of the exported UI widget manifest checked for generation "
                    + "affinity before boot (default: " + DEFAULT_UI_MANIFEST + ")")
    private Path uiManifest;

    private HeronLauncher.ShutdownWaiter shutdownWaiter = HeronLauncher::awaitShutdown;

    @Spec
    private CommandSpec commandSpec;

    /**
     * Creates the picocli start command.
     */
    public StartCommand() {
        // populated by picocli
    }

    /**
     * Parses a start command using the standard Heron picocli command tree.
     *
     * @param arguments arguments supplied after the executable name
     * @return the parsed start command
     * @throws picocli.CommandLine.ParameterException if parsing or validation fails
     */
    public static StartCommand parse(String[] arguments) {
        picocli.CommandLine commandLine = new picocli.CommandLine(new HeronLauncher());
        picocli.CommandLine.ParseResult result = commandLine.parseArgs(arguments);
        if (!result.hasSubcommand()) {
            throw new picocli.CommandLine.ParameterException(commandLine, "command 'start' is required");
        }
        picocli.CommandLine subcommand = commandLine.getSubcommands().get("start");
        if (!result.subcommand().commandSpec().equals(subcommand.getCommandSpec())) {
            throw new picocli.CommandLine.ParameterException(commandLine, "unsupported command");
        }
        return subcommand.getCommand();
    }

    /**
     * Returns the validated YAML configuration path.
     *
     * @return the validated YAML configuration path
     */
    public Path configuration() {
        return configuration;
    }

    /**
     * Returns the validated HTTP bind port.
     *
     * @return the validated HTTP bind port
     */
    public int port() {
        return port;
    }

    /**
     * Replaces the shutdown waiter (test seam: production blocks on the JVM
     * shutdown hook).
     *
     * @param waiter the shutdown waiter to use
     */
    void shutdownWaiter(HeronLauncher.ShutdownWaiter waiter) {
        this.shutdownWaiter = java.util.Objects.requireNonNull(waiter, "waiter must not be null");
    }

    @Override
    public Integer call() {
        Integer invalid = validateSourceOptions();
        if (invalid != null) {
            return invalid;
        }
        if (applicationId != null) {
            return startFromStore();
        }
        return HeronLauncher.run(this, ApplicationLoader::load,
                HeronLauncher::createHostAdapter, HeronLauncher::awaitShutdown);
    }

    private Integer validateSourceOptions() {
        if (configuration == null && applicationId == null) {
            commandSpec.commandLine().getErr().println("heron: either --config or --app is required");
            return 2;
        }
        if (configuration != null && applicationId != null) {
            commandSpec.commandLine().getErr().println("heron: --config and --app are mutually exclusive");
            return 2;
        }
        return null;
    }

    private Integer startFromStore() {
        return new GenerationStarter(store, applicationId, generationId, port, uiManifest,
                commandSpec, shutdownWaiter).start();
    }
}
