package dev.hogwai.platform.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies descriptor generation and compile-time validation rules.
 */
class HeronServiceProcessorTest {

    private static final String PROVIDER_FACTORY = "dev.hogwai.platform.spi.provider.ProviderFactory";

    @TempDir
    Path workdir;

    @Test
    void generatesDescriptorForAnnotatedProviderFactory() throws IOException {
        Compilation compilation = compile("demo.orders.DemoOrdersProviderFactory", """
                package demo.orders;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.orders")
                public class DemoOrdersProviderFactory implements ProviderFactory {
                    public dev.hogwai.platform.spi.provider.ProviderDescriptor descriptor() {
                        return null;
                    }
                
                    public java.util.List<dev.hogwai.platform.spi.Diagnostic> validate(java.util.Map<String, Object> rawConfig) {
                        return java.util.List.of();
                    }
                
                    public dev.hogwai.platform.spi.provider.CapabilityInstance create(
                            java.util.Map<String, Object> rawConfig, dev.hogwai.platform.spi.provider.BuildContext context) {
                        return null;
                    }
                }
                """);
        assertThat(compilation.success()).isTrue();
        assertThat(compilation.descriptor(PROVIDER_FACTORY))
                .isEqualTo("demo.orders.DemoOrdersProviderFactory" + System.lineSeparator());
    }

    @Test
    void rejectsClassNotImplementingDeclaredContract() throws IOException {
        Compilation compilation = compile("demo.bad.NotAProvider", """
                package demo.bad;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.bad")
                public class NotAProvider {
                }
                """);
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("does not implement its declared service contract"));
        assertThat(compilation.descriptorOrNull(PROVIDER_FACTORY)).isNull();
    }

    @Test
    void rejectsUnsupportedContract() throws IOException {
        Compilation compilation = compile("demo.bad.UnsupportedContract", """
                package demo.bad;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                
                @HeronService(value = Runnable.class, id = "demo.bad")
                public class UnsupportedContract implements Runnable {
                    public void run() {
                    }
                }
                """);
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("declares unsupported service contract"));
    }

    @Test
    void rejectsAbstractClass() throws IOException {
        Compilation compilation = compile("demo.bad.AbstractProvider", """
                package demo.bad;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.bad")
                public abstract class AbstractProvider implements ProviderFactory {
                }
                """);
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("must not be abstract"));
    }

    @Test
    void rejectsPrivateConstructor() throws IOException {
        Compilation compilation = compile("demo.bad.HiddenConstructor", """
                package demo.bad;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.bad")
                public class HiddenConstructor implements ProviderFactory {
                    private HiddenConstructor() {
                    }
                
                    public dev.hogwai.platform.spi.provider.ProviderDescriptor descriptor() {
                        return null;
                    }
                
                    public java.util.List<dev.hogwai.platform.spi.Diagnostic> validate(java.util.Map<String, Object> rawConfig) {
                        return java.util.List.of();
                    }
                
                    public dev.hogwai.platform.spi.provider.CapabilityInstance create(
                            java.util.Map<String, Object> rawConfig, dev.hogwai.platform.spi.provider.BuildContext context) {
                        return null;
                    }
                }
                """);
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("public no-argument constructor"));
    }

    @Test
    void rejectsNonPublicClass() throws IOException {
        Compilation compilation = compile("demo.bad.PackagePrivate", """
                package demo.bad;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.bad")
                class PackagePrivate implements ProviderFactory {
                }
                """);
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("must be public"));
    }

    @Test
    void rejectsNonCanonicalVersion() throws IOException {
        Compilation compilation = compile("demo.bad.BadVersion", """
                package demo.bad;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.bad", version = "1.0")
                public class BadVersion implements ProviderFactory {
                    public dev.hogwai.platform.spi.provider.ProviderDescriptor descriptor() {
                        return null;
                    }
                
                    public java.util.List<dev.hogwai.platform.spi.Diagnostic> validate(java.util.Map<String, Object> rawConfig) {
                        return java.util.List.of();
                    }
                
                    public dev.hogwai.platform.spi.provider.CapabilityInstance create(
                            java.util.Map<String, Object> rawConfig, dev.hogwai.platform.spi.provider.BuildContext context) {
                        return null;
                    }
                }
                """);
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("canonical major.minor.patch"));
    }

    @Test
    void rejectsDuplicateIdForSameContract() throws IOException {
        String factoryTemplate = """
                package demo.dup;
                
                import dev.hogwai.platform.spi.annotation.HeronService;
                import dev.hogwai.platform.spi.provider.ProviderFactory;
                
                @HeronService(value = ProviderFactory.class, id = "demo.same")
                public class %s implements ProviderFactory {
                    public dev.hogwai.platform.spi.provider.ProviderDescriptor descriptor() {
                        return null;
                    }
                
                    public java.util.List<dev.hogwai.platform.spi.Diagnostic> validate(java.util.Map<String, Object> rawConfig) {
                        return java.util.List.of();
                    }
                
                    public dev.hogwai.platform.spi.provider.CapabilityInstance create(
                            java.util.Map<String, Object> rawConfig, dev.hogwai.platform.spi.provider.BuildContext context) {
                        return null;
                    }
                }
                """;
        Compilation compilation = compile(
                "demo.dup.FirstFactory", factoryTemplate.formatted("FirstFactory"),
                "demo.dup.SecondFactory", factoryTemplate.formatted("SecondFactory"));
        assertThat(compilation.success()).isFalse();
        assertThat(compilation.errors()).singleElement()
                .satisfies(message -> assertThat(message).contains("duplicate id 'demo.same'"));
    }

    private Compilation compile(String firstQualifiedName, String firstSource, String... moreQualifiedNameAndSource)
            throws IOException {
        List<String[]> units = new ArrayList<>();
        units.add(new String[]{firstQualifiedName, firstSource});
        for (int index = 0; index < moreQualifiedNameAndSource.length; index += 2) {
            units.add(new String[]{moreQualifiedNameAndSource[index], moreQualifiedNameAndSource[index + 1]});
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_PATH, classpathEntries());
            List<String> options = List.of("-d", workdir.resolve("classes").toString(),
                    "-processor", HeronServiceProcessor.class.getName());
            List<JavaFileObject> sources = new ArrayList<>();
            for (String[] unit : units) {
                sources.add(new StringSource(unit[0], unit[1]));
            }
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, diagnostics, options, null, sources);
            boolean success = Boolean.TRUE.equals(task.call());
            return new Compilation(success, diagnostics.getDiagnostics(), workdir.resolve("classes"));
        }
    }

    private List<File> classpathEntries() {
        List<Path> entries = new ArrayList<>();
        try {
            ClassLoader loader = HeronServiceProcessorTest.class.getClassLoader();
            if (loader instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    entries.add(Path.of(url.toURI()));
                }
            } else {
                for (String entry : System.getProperty("java.class.path").split(File.pathSeparator, -1)) {
                    entries.add(Path.of(entry));
                }
            }
        } catch (URISyntaxException failure) {
            throw new IllegalStateException("cannot resolve test classpath", failure);
        }
        return entries.stream().map(Path::toFile).toList();
    }

    private record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                               Path classesDirectory) {

        List<String> errors() {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> diagnostic.getMessage(null))
                    .toList();
        }

        String descriptor(String contract) throws IOException {
            return Files.readString(descriptorPath(contract));
        }

        String descriptorOrNull(String contract) throws IOException {
            Path path = descriptorPath(contract);
            return Files.exists(path) ? Files.readString(path) : null;
        }

        private Path descriptorPath(String contract) {
            return classesDirectory.resolve("META-INF").resolve("services").resolve(contract);
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {

        private final String content;

        StringSource(String qualifiedName, String content) {
            super(URI.create("mem:///%s.java".formatted(qualifiedName.replace('.', '/'))), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
}
