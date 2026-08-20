package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.runtime.config.capability.CapabilityConfig;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParsedApplicationTest {

    private static final ApplicationConfig APP = new ApplicationConfig(
            "platform.dev/v1alpha1", "Application", "demo", List.of());

    private static final Diagnostic ERROR = new Diagnostic(
            PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, "/kind", "unsupported kind", "use 'Application'");

    @Test
    void validApplicationHasNoDiagnostics() {
        ParsedApplication result = new ParsedApplication(APP, List.of());

        assertThat(result.isValid()).isTrue();
        assertThat(result.application()).isSameAs(APP);
        assertThat(result.diagnostics()).isEmpty();
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
        CapabilityConfig cc = new CapabilityConfig("id", CapabilityKind.SOURCE, "acme", "1.2.3",
                Map.of("s", "str", "b", true, "l", 42L), List.of());
        assertThat(cc.config()).containsEntry("s", "str");
    }
}
