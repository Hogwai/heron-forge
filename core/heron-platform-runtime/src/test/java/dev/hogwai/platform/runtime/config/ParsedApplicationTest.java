package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedApplicationTest {

    private static final ApplicationConfig APP = new ApplicationConfig(
            "heron.dev/v1", "demo", List.of());

    private static final Diagnostic ERROR = new Diagnostic(
            PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, "/kind", "unsupported kind", "use 'Application'");

    @Test
    void validApplicationHasNoDiagnostics() {
        ParsedApplication result = new ParsedApplication(APP, List.of());

        assertThat(result.isValid()).isTrue();
        assertThat(result.application()).isSameAs(APP);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(APP.entrypoints()).isEmpty();
    }

    @Test
    void invalidApplicationHasDiagnosticsAndNullApplication() {
        ParsedApplication result = new ParsedApplication(null, List.of(ERROR));

        assertThat(result.isValid()).isFalse();
        assertThat(result.application()).isNull();
        assertThat(result.diagnostics()).containsExactly(ERROR);
    }

    @Test
    void rejectsApplicationWithDiagnostics() {
        List<Diagnostic> diagnostics = List.of(ERROR);
        assertThatThrownBy(() -> new ParsedApplication(APP, diagnostics))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullApplicationWithoutDiagnostics() {
        List<Diagnostic> noDiagnostics = List.of();
        assertThatThrownBy(() -> new ParsedApplication(null, noDiagnostics))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capabilityConfigStillAcceptsCanonicalScalars() {
        CapabilityConfig cc = new CapabilityConfig("id", "acme", "1.2.3",
                Map.of("s", "str", "b", true, "l", 42L), List.of());
        assertThat(cc.config()).containsEntry("s", "str");
    }

    @Test
    void fiveArgumentApplicationConstructorStoresEntrypoints() {
        EntrypointConfig entrypoint = new EntrypointConfig("exceptions", "GET", "/exceptions", "detector");

        ApplicationConfig application = new ApplicationConfig(
                "heron.dev/v1", "demo", List.of(), List.of(entrypoint));

        assertThat(application.entrypoints()).containsExactly(entrypoint);
    }

    @Test
    void applicationEntrypointsAreDefensivelyCopiedAndImmutable() {
        List<EntrypointConfig> entrypoints = new ArrayList<>();
        entrypoints.add(new EntrypointConfig("exceptions", "GET", "/exceptions", "detector"));

        ApplicationConfig application = new ApplicationConfig(
                "heron.dev/v1", "demo", List.of(), entrypoints);
        entrypoints.add(new EntrypointConfig("health", "GET", "/health", "detector"));

        assertThat(application.entrypoints()).hasSize(1);
        var entryPoint = new EntrypointConfig("health", "GET", "/health", "detector");
        var entryPoints = application.entrypoints();
        assertThatThrownBy(() -> entryPoints.add(entryPoint))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsEntrypointsInTheApplicationEnvelope() {
        ParsedApplication result = parse("""
                apiVersion: heron.dev/v1
                application: supply-chain-demo
                capabilities: []
                endpoints:
                  - id: exceptions
                    method: GET
                    path: /exceptions
                    target: detector
                """);

        assertThat(result.isValid()).isTrue();
        assertThat(result.application().name()).isEqualTo("supply-chain-demo");
        assertThat(result.application().entrypoints())
                .containsExactly(new EntrypointConfig("exceptions", "GET", "/exceptions", "detector"));
    }

    @Test
    void missingEntrypointsRemainAnEmptyList() {
        ParsedApplication result = parse("""
                apiVersion: heron.dev/v1
                application: supply-chain-demo
                capabilities: []
                """);

        assertThat(result.isValid()).isTrue();
        assertThat(result.application().entrypoints()).isEmpty();
    }

    @Test
    void rejectsUnknownRootFieldsWithoutChangingTheEnvelope() {
        ParsedApplication result = parse("""
                apiVersion: heron.dev/v1
                application: supply-chain-demo
                capabilities: []
                endpoints: []
                unsupportedRoot: 1
                """);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics()).extracting(Diagnostic::path)
                .containsExactly("/<key>");
    }

    private static ParsedApplication parse(String yaml) {
        return new SafeYamlParser().parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
