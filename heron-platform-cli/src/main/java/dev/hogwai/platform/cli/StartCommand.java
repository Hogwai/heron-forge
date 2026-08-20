package dev.hogwai.platform.cli;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import dev.hogwai.platform.runtime.load.ApplicationLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

/** Picocli subcommand that starts the standard Heron host. */
@Command(name = "start", description = "Start the configured Heron application")
public final class StartCommand implements Callable<Integer> {

    /** Default HTTP port used when no explicit port is supplied. */
    public static final int DEFAULT_PORT = 8080;

    @Option(names = "--config", required = true, converter = ReadablePathConverter.class,
            description = "readable YAML application configuration")
    private Path configuration;

    @Option(names = "--port", defaultValue = "" + DEFAULT_PORT, converter = PortConverter.class,
            description = "HTTP bind port (0..65535)")
    private int port;

    /** Creates the picocli start command. */
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
        return (StartCommand) subcommand.getCommand();
    }

    /** Returns the validated YAML configuration path.
     *
     * @return the validated YAML configuration path
     */
    public Path configuration() {
        return configuration;
    }

    /** Returns the validated HTTP bind port.
     *
     * @return the validated HTTP bind port
     */
    public int port() {
        return port;
    }

    @Override
    public Integer call() {
        return HeronLauncher.run(this, ApplicationLoader::load,
                HeronLauncher::createHostAdapter, HeronLauncher::awaitShutdown);
    }

    private static final class ReadablePathConverter implements ITypeConverter<Path> {
        @Override
        public Path convert(String value) {
            try {
                Path path = Path.of(value);
                if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                    throw new TypeConversionException("configuration file is not readable");
                }
                return path;
            } catch (InvalidPathException | SecurityException exception) {
                throw new TypeConversionException("configuration file path is invalid");
            }
        }
    }

    private static final class PortConverter implements ITypeConverter<Integer> {
        @Override
        public Integer convert(String value) {
            final int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new TypeConversionException("port must be 0 through 65535");
            }
            if (parsed < 0 || parsed > 65535) {
                throw new TypeConversionException("port must be 0 through 65535");
            }
            return parsed;
        }
    }

}
