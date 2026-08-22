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

/** Picocli command that scaffolds a Heron provider plugin. */
@SuppressWarnings("PMD.CyclomaticComplexity")
@Command(name = "provider", description = "Scaffold a new Heron provider plugin")
public final class CreateProviderCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProviderCommand.class);

    private static final String PROVIDER_SERVICE =
            "src/main/resources/META-INF/services/dev.hogwai.platform.spi.provider.ProviderFactory";

    private final Path baseDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = "provider project name")
    String name;

    @Option(names = "--package", description = "base Java package")
    String packageName;

    @Option(names = "--kind", description = "capability kind: SOURCE or TRANSFORM", defaultValue = "SOURCE")
    String kind;

    /** Creates a provider command rooted at the current directory. */
    public CreateProviderCommand() {
        this(Path.of(""));
    }

    /** Creates a provider command rooted at the given directory for tests. */
    CreateProviderCommand(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Integer call() {
        try {
            String projectName = ProjectNames.validateProjectName(name);
            String basePackage = resolveBasePackage(projectName);
            String capabilityKind = normalizeKind(kind);
            Map<String, String> files = renderFiles(projectName, basePackage, capabilityKind);
            ProjectWriter.writeAll(baseDirectory.resolve(projectName), files);
            LOGGER.info("Created Heron provider project '{}'", projectName);
            return 0;
        } catch (IOException | SecurityException | IllegalArgumentException | IllegalStateException failure) {
            LOGGER.error("create provider failed: {}", failure.getMessage());
            return 1;
        }
    }

    private String resolveBasePackage(String projectName) {
        return packageName == null || packageName.isBlank()
                ? ProjectNames.derivePackage(projectName)
                : ProjectNames.validatePackage(packageName);
    }

    private static Map<String, String> renderFiles(String projectName, String basePackage,
            String capabilityKind) throws IOException {
        Map<String, String> model = new LinkedHashMap<>(PlatformVersions.templateModel());
        model.put("projectName", projectName);
        model.put("basePackage", basePackage);
        model.put("className", ProjectNames.toJavaTypeName(projectName));
        model.put("kind", capabilityKind);
        model.put("inputPorts", inputPorts(capabilityKind));
        String className = ProjectNames.toJavaTypeName(projectName);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("settings.gradle.kts", render("provider/settings.gradle.kts.template", model));
        files.put("build.gradle.kts", render("provider/build.gradle.kts.template", model));
        files.put("%s%sProviderFactory.java".formatted(sourcePath(basePackage, "main"), className),
                render("provider/ProviderFactory.java.template", model));
        files.put("%s%sProviderFactoryTest.java".formatted(sourcePath(basePackage, "test"), className),
                render("provider/ProviderFactoryTest.java.template", model));
        files.put(PROVIDER_SERVICE,
                render("provider/provider-service.template", model));
        return files;
    }

    private static String sourcePath(String basePackage, String sourceSet) {
        return "src/%s/java/%s/".formatted(sourceSet, basePackage.replace('.', '/'));
    }

    private static String normalizeKind(String value) {
        String normalized = value == null || value.isBlank() ? "SOURCE" : value.toUpperCase(Locale.ROOT);
        if (!normalized.equals("SOURCE") && !normalized.equals("TRANSFORM")) {
            throw new IllegalArgumentException("provider kind must be SOURCE or TRANSFORM");
        }
        return normalized;
    }

    private static String inputPorts(String kind) {
        if (kind.equals("SOURCE")) {
            return "Map.of()";
        }
        return "Map.of(new PortId(\"input\"), new PortDescriptor(new PortId(\"input\"), SCHEMA, true))";
    }

    private static String render(String template, Map<String, String> model) throws IOException {
        return TemplateRenderer.render(TemplateRenderer.load(template), model);
    }
}
