package dev.hogwai.platform.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/** Picocli command that scaffolds a Heron data or host brick. */
@Command(name = "brick", description = "Scaffold a new Heron data or host brick")
public final class CreateBrickCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateBrickCommand.class);

    private final Path baseDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = "brick project name")
    String name;

    @Option(names = "--package", description = "base Java package")
    String packageName;

    @Option(names = "--type", description = "brick type: data or host")
    String type;

    /** Creates a brick command rooted at the current directory. */
    public CreateBrickCommand() {
        this(Path.of(""));
    }

    /** Creates a brick command rooted at the given directory for tests. */
    CreateBrickCommand(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Integer call() {
        try {
            String brickType = normalizeType(type);
            String projectName = ProjectNames.validateProjectName(name);
            String basePackage = packageName == null || packageName.isBlank()
                    ? ProjectNames.derivePackage(projectName)
                    : ProjectNames.validatePackage(packageName);
            String className = ProjectNames.toJavaTypeName(projectName);
            Map<String, String> model = new LinkedHashMap<>(PlatformVersions.templateModel());
            model.put("projectName", projectName);
            model.put("basePackage", basePackage);
            model.put("className", className);
            Map<String, String> files = new LinkedHashMap<>();
            files.put("settings.gradle.kts", render("brick/settings.gradle.kts.template", model));
            files.put("build.gradle.kts", render("brick/build.gradle.kts.template", model));
            if (brickType.equals("data")) {
                files.put("src/main/java/%s/%sDataAccessFactory.java"
                                .formatted(basePackage.replace('.', '/'), className),
                        render("brick/DataAccessFactory.java.template", model));
                files.put("src/main/resources/META-INF/services/dev.hogwai.platform.spi.data.access.DataAccessFactory",
                        render("brick/data-service.template", model));
            } else {
                files.put("src/main/java/%s/%sHostAdapter.java"
                                .formatted(basePackage.replace('.', '/'), className),
                        render("brick/HostAdapter.java.template", model));
                files.put("src/main/resources/META-INF/services/dev.hogwai.platform.spi.host.HostAdapter",
                        render("brick/host-service.template", model));
            }
            ProjectWriter.writeAll(baseDirectory.resolve(projectName), files);
            LOGGER.info("Created Heron {} brick project '{}'", brickType, projectName);
            return 0;
        } catch (IOException | SecurityException | IllegalArgumentException | IllegalStateException failure) {
            LOGGER.error("create brick failed: {}", failure.getMessage());
            return 1;
        }
    }

    private static String normalizeType(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!normalized.equals("data") && !normalized.equals("host")) {
            throw new IllegalArgumentException("brick type must be data or host");
        }
        return normalized;
    }

    private static String render(String template, Map<String, String> model) throws IOException {
        return TemplateRenderer.render(TemplateRenderer.load(template), model);
    }
}
