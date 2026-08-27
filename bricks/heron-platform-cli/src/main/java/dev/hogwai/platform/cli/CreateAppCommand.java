package dev.hogwai.platform.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Picocli command that scaffolds a Heron application project.
 */
@Command(name = "app", description = "Scaffold a new Heron application project")
public final class CreateAppCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateAppCommand.class);

    private final Path baseDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = "project name")
    String name;

    @Option(names = "--package", description = "base Java package for the sample provider")
    String packageName;

    /**
     * Creates an application command rooted at the current directory.
     */
    public CreateAppCommand() {
        this(Path.of(""));
    }

    /**
     * Creates an application command rooted at the given directory for tests.
     */
    CreateAppCommand(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Integer call() {
        try {
            String projectName = ProjectNames.validateProjectName(name);
            String basePackage = packageName == null || packageName.isBlank()
                    ? ProjectNames.derivePackage(projectName)
                    : ProjectNames.validatePackage(packageName);
            Map<String, String> model = new LinkedHashMap<>(PlatformVersions.templateModel());
            model.put("projectName", projectName);
            model.put("basePackage", basePackage);
            Map<String, String> files = new LinkedHashMap<>();
            files.put("settings.gradle.kts", render("settings.gradle.kts.template", model));
            files.put("build.gradle.kts", render("build.gradle.kts.template", model));
            files.put("src/main/resources/application.yaml", render("application.yaml.template", model));
            files.put("src/main/java/%s/HelloProviderFactory.java".formatted(basePackage.replace('.', '/')),
                    render("HelloProviderFactory.java.template", model));
            ProjectWriter.writeAll(baseDirectory.resolve(projectName), files);
            LOGGER.info("Created Heron application project '{}'", projectName);
            LOGGER.info("Next steps:");
            LOGGER.info("  cd {}", projectName);
            LOGGER.info("  gradle installDist");
            return 0;
        } catch (IOException | SecurityException | IllegalArgumentException | IllegalStateException failure) {
            LOGGER.error("create app failed: {}", failure.getMessage());
            return 1;
        }
    }

    private static String render(String template, Map<String, String> model) throws IOException {
        return TemplateRenderer.render(TemplateRenderer.load(template), model);
    }
}
