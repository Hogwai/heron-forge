package dev.hogwai.platform.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Compatibility command for the former application-only starter.
 */
@Command(name = "init", description = "Deprecated alias for create app")
public final class InitCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitCommand.class);

    /**
     * Platform version exposed for existing tests and generated-build assertions.
     */
    static final String PLATFORM_VERSION = PlatformVersions.value("platform.version");

    /**
     * SLF4J version exposed for existing tests and generated-build assertions.
     */
    static final String SLF4J_VERSION = PlatformVersions.value("slf4j.version");

    private final Path baseDirectory;

    @Parameters(index = "0", paramLabel = "NAME", description = "project name and target directory")
    String name;

    @Option(names = "--package", description = "base Java package for the sample provider")
    String packageName;

    /**
     * Creates a compatibility command rooted at the current directory.
     */
    public InitCommand() {
        this(Path.of(""));
    }

    /**
     * Creates a compatibility command rooted at the given directory for tests.
     */
    InitCommand(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Integer call() {
        LOGGER.warn("'init' is deprecated; use 'heron create app' instead");
        CreateAppCommand delegate = new CreateAppCommand(baseDirectory);
        delegate.name = name;
        delegate.packageName = packageName;
        return delegate.call();
    }
}
