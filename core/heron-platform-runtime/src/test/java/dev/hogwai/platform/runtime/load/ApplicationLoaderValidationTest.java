package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLoaderValidationTest {

    @Test
    void validatesWithoutCreatingProviderInstances() {
        CountingFactory factory = new CountingFactory(List.of());

        ValidationReport report = ApplicationLoader.validate(stream(yaml("source")), registry(factory));

        assertThat(report.valid()).isTrue();
        assertThat(report.diagnostics()).isEmpty();
        assertThat(factory.createCalls()).hasValue(0);
    }

    @Test
    void preservesProviderValidationWarningsWithoutCreatingInstances() {
        Diagnostic warning = new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.WARNING,
                "/config/host", "host is deprecated", "use the replacement field");
        CountingFactory factory = new CountingFactory(List.of(warning));

        ValidationReport report = ApplicationLoader.validate(stream(yaml("source")), registry(factory));

        assertThat(report.valid()).isTrue();
        assertThat(report.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
            assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
        });
        assertThat(factory.createCalls()).hasValue(0);
    }

    @Test
    void reportsInvalidEntrypointTargetWithTargetPath() {
        CountingFactory factory = new CountingFactory(List.of());

        ValidationReport report = ApplicationLoader.validate(stream(yaml("missing")), registry(factory));

        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
            assertThat(diagnostic.path()).isEqualTo("/endpoints/0/target");
        });
        assertThat(factory.createCalls()).hasValue(0);
    }

    @Test
    void aggregatesProviderWarningAndEntrypointErrorWhenCompilationFails() {
        Diagnostic warning = new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.WARNING,
                "/config/host", "host is deprecated", "use the replacement field");
        CountingFactory factory = new CountingFactory(List.of(warning),
                SnapshotBuilderTestSupport.transform("warning-provider", "1.0.0"));

        ValidationReport report = ApplicationLoader.validate(
                stream(yamlWithCycleAndMissingTarget()), registry(factory));

        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).extracting(Diagnostic::severity)
                .contains(Severity.WARNING, Severity.ERROR);
        assertThat(report.diagnostics()).extracting(Diagnostic::path)
                .contains("/endpoints/0/target");
        assertThat(report.diagnostics()).extracting(Diagnostic::code)
                .contains(PlatformErrorCode.GRAPH_CYCLE_ERROR, PlatformErrorCode.GRAPH_REFERENCE_ERROR);
        assertThat(factory.createCalls()).hasValue(0);
    }

    @Test
    void reportsExplicitDuplicateCapabilityId() {
        CountingFactory factory = new CountingFactory(List.of());

        ValidationReport report = ApplicationLoader.validate(
                stream(yamlWithDuplicateCapability("source")), registry(factory));

        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).extracting(Diagnostic::message)
                .contains("duplicate capability id");
        assertThat(factory.createCalls()).hasValue(0);
    }

    @Test
    void returnsParseAndProviderDiagnosticsWithoutCreatingInstances() {
        CountingFactory factory = new CountingFactory(List.of(new Diagnostic(
                PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, "/config/host", "invalid", null)));

        ValidationReport providerReport = ApplicationLoader.validate(stream(yaml("source")), registry(factory));
        ValidationReport parseReport = ApplicationLoader.validate(stream("not: [valid"), registry(factory));

        assertThat(providerReport.diagnostics()).singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR));
        assertThat(parseReport.diagnostics()).isNotEmpty();
        assertThat(factory.createCalls()).hasValue(0);
    }

    @Test
    void publicValidationMethodReturnsValidationReport() throws NoSuchMethodException {
        Method method = ApplicationLoader.class.getMethod("validate", java.io.InputStream.class);

        assertThat(method.getReturnType()).isEqualTo(ValidationReport.class);
    }

    private static ProviderRegistry registry(CountingFactory factory) {
        return new SnapshotBuilderTestRegistry(new ProviderRegistry.Registration(factory, factory.descriptor()));
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String yaml(String target) {
        return """
                apiVersion: heron.dev/v1
                application: validation-test
                capabilities:
                  - id: source
                    provider:
                      id: source
                      version: 1.0.0
                    config:
                      host: localhost
                endpoints:
                  - id: read
                    method: GET
                    path: /read
                    target: %s
                """.formatted(target);
    }

    private static String yamlWithDuplicateCapability(String target) {
        return """
                apiVersion: heron.dev/v1
                application: validation-test
                capabilities:
                  - id: source
                    provider:
                      id: source
                      version: 1.0.0
                    config:
                      host: localhost
                  - id: source
                    provider:
                      id: source
                      version: 1.0.0
                    config:
                      host: localhost
                endpoints:
                  - id: read
                    method: GET
                    path: /read
                    target: %s
                """.formatted(target);
    }

    private static String yamlWithCycleAndMissingTarget() {
        return """
                apiVersion: heron.dev/v1
                application: validation-test
                capabilities:
                  - id: a
                    provider:
                      id: warning-provider
                      version: 1.0.0
                    config:
                      host: localhost
                    inputs:
                      in:
                        capability: b
                        port: out
                  - id: b
                    provider:
                      id: warning-provider
                      version: 1.0.0
                    config:
                      host: localhost
                    inputs:
                      in:
                        capability: a
                        port: out
                endpoints:
                  - id: read
                    method: GET
                    path: /read
                    target: missing
                """;
    }

    private static final class CountingFactory implements ProviderFactory {
        private final List<Diagnostic> validationDiagnostics;
        private final AtomicInteger createCalls = new AtomicInteger();
        private final ProviderDescriptor descriptor;

        private CountingFactory(List<Diagnostic> validationDiagnostics) {
            this(validationDiagnostics, SnapshotBuilderTestSupport.source("source", "1.0.0"));
        }

        private CountingFactory(List<Diagnostic> validationDiagnostics, ProviderDescriptor descriptor) {
            this.validationDiagnostics = List.copyOf(validationDiagnostics);
            this.descriptor = descriptor;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public List<Diagnostic> validate(Map<String, Object> rawConfig) {
            return validationDiagnostics;
        }

        @Override
        public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
            createCalls.incrementAndGet();
            return new SnapshotBuilderTestInstance("source", List.of(), false);
        }

        private AtomicInteger createCalls() {
            return createCalls;
        }

    }
}
