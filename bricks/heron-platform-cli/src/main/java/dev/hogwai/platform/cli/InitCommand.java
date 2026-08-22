package dev.hogwai.platform.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Picocli subcommand that scaffolds a new Heron application project.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
@Command(name = "init", description = "Scaffold a new Heron application project")
public final class InitCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitCommand.class);

    /**
     * Platform version referenced by the generated build script.
     */
    static final String PLATFORM_VERSION = versions().getProperty("platform.version");

    /**
     * SLF4J version referenced by the generated build script.
     */
    static final String SLF4J_VERSION = versions().getProperty("slf4j.version");

    private final Path baseDirectory;

    @Parameters(index = "0", paramLabel = "NAME", description = "project name and target directory")
    String name;

    @Option(names = "--package", description = "base Java package for the sample provider (default: derived from NAME)")
    String packageName;

    /**
     * Creates the picocli init command.
     */
    public InitCommand() {
        this(Path.of(""));
    }

    /**
     * Creates an init command rooted at the given base directory, for tests.
     */
    InitCommand(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Integer call() {
        try {
            String projectName = validateName(name);
            String basePackage = packageName == null || packageName.isBlank()
                    ? derivePackage(projectName)
                    : validatePackage(packageName);
            Path target = baseDirectory.resolve(projectName);
            if (Files.exists(target)) {
                try (Stream<Path> files = Files.list(target)) {
                    if (files.findAny().isPresent()) {
                        LOGGER.error("target directory '{}' already exists and is not empty", projectName);
                        return 1;
                    }
                }
            }
            Files.createDirectories(target);
            Map<String, String> model = Map.of(
                    "projectName", projectName,
                    "basePackage", basePackage,
                    "platformVersion", PLATFORM_VERSION,
                    "slf4jVersion", SLF4J_VERSION);
            write(target, "settings.gradle.kts", render(load("settings.gradle.kts.template"), model));
            write(target, "build.gradle.kts", render(load("build.gradle.kts.template"), model));
            write(target, "src/main/resources/application.yaml",
                    render(load("application.yaml.template"), model));
            write(target, "src/main/resources/META-INF/services/dev.hogwai.platform.spi.provider.ProviderFactory",
                    render(load("ProviderFactory.template"), model));
            write(target, "src/main/java/%s/HelloProviderFactory.java"
                            .formatted(basePackage.replace('.', '/')),
                    render(load("HelloProviderFactory.java.template"), model));
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            LOGGER.error("init failed: {}", failure.getMessage());
            return 1;
        }

        LOGGER.info("Created Heron project '{}'", name);
        LOGGER.info("Next steps:");
        LOGGER.info("  cd {}", name);
        LOGGER.info("  ./gradlew installDist");
        LOGGER.info("  ./build/install/{}/bin/{} start --config src/main/resources/application.yaml", name, name);
        return 0;
    }

    private static Properties versions() {
        Properties properties = new Properties();
        try (InputStream input = InitCommand.class.getResourceAsStream("/heron-platform.properties")) {
            if (input == null) {
                throw new IllegalStateException("heron-platform.properties not found on the classpath");
            }
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to load heron-platform.properties", failure);
        }
        return properties;
    }

    private static void write(Path target, String relative, String content) throws IOException {
        Path file = target.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String validateName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project name must not be blank");
        }
        if (value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("project name must be a simple directory name");
        }
        return value;
    }

    private static String validatePackage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("package must not be blank");
        }
        for (String part : value.split("\\.", -1)) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))
                    || part.chars().skip(1).anyMatch(ch -> !Character.isJavaIdentifierPart(ch))) {
                throw new IllegalArgumentException("package must be a valid Java package name");
            }
        }
        return value;
    }

    private static String derivePackage(String projectName) {
        String sanitized = projectName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return sanitized.isEmpty() ? "app" : sanitized;
    }

    private static String load(String name) throws IOException {
        try (InputStream input = InitCommand.class.getResourceAsStream("/templates/%s".formatted(name))) {
            if (input == null) {
                throw new IOException("template not found: %s".formatted(name));
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String render(String template, Map<String, String> model) {
        String rendered = template;
        for (Map.Entry<String, String> entry : model.entrySet()) {
            rendered = rendered.replace("{{%s}}".formatted(entry.getKey()), entry.getValue());
        }
        return rendered;
    }
}