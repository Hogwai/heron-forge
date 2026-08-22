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

    @Option(names = "--package", description = "base package")
    String packageName;

    @Option(names = "--kind", description = "capability kind: SOURCE or TRANSFORM", defaultValue = "SOURCE")
    String kind;

    @Option(names = "--language", description = "source language: JAVA or KOTLIN", defaultValue = "JAVA")
    String language;

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
            String sourceLanguage = normalizeLanguage(language);
            Map<String, String> files = renderFiles(projectName, basePackage, capabilityKind, sourceLanguage);
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
            String capabilityKind, String sourceLanguage) throws IOException {
        Map<String, String> model = new LinkedHashMap<>(PlatformVersions.templateModel());
        model.put("projectName", projectName);
        model.put("basePackage", basePackage);
        model.put("className", ProjectNames.toJavaTypeName(projectName));
        model.put("kind", capabilityKind);
        model.put("inputPorts", inputPorts(capabilityKind, sourceLanguage));
        String className = ProjectNames.toJavaTypeName(projectName);
        boolean kotlin = sourceLanguage.equals("KOTLIN");
        String sourceExtension = kotlin ? "kt" : "java";
        String sourceTemplate = kotlin ? "provider/ProviderFactory.kt.template" : "provider/ProviderFactory.java.template";
        String testTemplate = kotlin ? "provider/ProviderFactoryTest.kt.template"
                : "provider/ProviderFactoryTest.java.template";
        Map<String, String> files = new LinkedHashMap<>();
        files.put("settings.gradle.kts", render("provider/settings.gradle.kts.template", model));
        files.put("build.gradle.kts", render(kotlin ? "provider/build-kotlin.gradle.kts.template"
                : "provider/build.gradle.kts.template", model));
        files.put("%s%sProviderFactory.%s".formatted(sourcePath(basePackage, "main", sourceLanguage), className,
                sourceExtension), render(sourceTemplate, model));
        files.put("%s%sProviderFactoryTest.%s".formatted(sourcePath(basePackage, "test", sourceLanguage), className,
                sourceExtension), render(testTemplate, model));
        files.put(PROVIDER_SERVICE,
                render("provider/provider-service.template", model));
        return files;
    }

    private static String sourcePath(String basePackage, String sourceSet, String sourceLanguage) {
        String sourceDirectory = sourceLanguage.equals("KOTLIN") ? "kotlin" : "java";
        return "src/%s/%s/%s/".formatted(sourceSet, sourceDirectory, basePackage.replace('.', '/'));
    }

    private static String normalizeKind(String value) {
        String normalized = value == null || value.isBlank() ? "SOURCE" : value.toUpperCase(Locale.ROOT);
        if (!normalized.equals("SOURCE") && !normalized.equals("TRANSFORM")) {
            throw new IllegalArgumentException("provider kind must be SOURCE or TRANSFORM");
        }
        return normalized;
    }

    private static String normalizeLanguage(String value) {
        String normalized = value == null || value.isBlank() ? "JAVA" : value.toUpperCase(Locale.ROOT);
        if (!normalized.equals("JAVA") && !normalized.equals("KOTLIN")) {
            throw new IllegalArgumentException("provider language must be JAVA or KOTLIN");
        }
        return normalized;
    }

    private static String inputPorts(String kind, String language) {
        if (kind.equals("SOURCE")) {
            return language.equals("KOTLIN") ? "emptyMap()" : "Map.of()";
        }
        if (language.equals("KOTLIN")) {
            return "mapOf(PortId(\"input\") to PortDescriptor(PortId(\"input\"), SCHEMA, true))";
        }
        return "Map.of(new PortId(\"input\"), new PortDescriptor(new PortId(\"input\"), SCHEMA, true))";
    }

    private static String render(String template, Map<String, String> model) throws IOException {
        return TemplateRenderer.render(TemplateRenderer.load(template), model);
    }
}
