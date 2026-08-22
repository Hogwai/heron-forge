package dev.hogwai.platform.spi;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformExceptionTest {

    public static final String SPEC_PROVIDERS_0 = "/spec/providers/0";

    @Test
    void carriesCodeAndDiagnostics() {
        Diagnostic diagnostic = new Diagnostic(
                PlatformErrorCode.CONFIG_PARSE_ERROR,
                Severity.ERROR,
                "/spec/capabilities/1/inputs/orders",
                "cannot parse input",
                "check the input schema");
        PlatformException exception =
                new PlatformException(PlatformErrorCode.CONFIG_PARSE_ERROR, List.of(diagnostic));

        assertThat(exception.code()).isEqualTo(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(exception.diagnostics()).containsExactly(diagnostic);
    }

    @Test
    void defensivelyCopiesDiagnostics() {
        Diagnostic first = new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, "first", null);
        Diagnostic second = new Diagnostic(PlatformErrorCode.CONFIG_SCHEMA_ERROR, Severity.WARNING, null, "second", null);

        List<Diagnostic> mutable = new ArrayList<>();
        mutable.add(first);
        PlatformException exception = new PlatformException(PlatformErrorCode.CONFIG_PARSE_ERROR, mutable);

        mutable.add(second);

        assertThat(exception.diagnostics()).containsExactly(first);
    }

    @Test
    @SuppressWarnings("all")
    void exposesImmutableDiagnostics() {
        Diagnostic diagnostic = new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, "first", null);
        PlatformException exception =
                new PlatformException(PlatformErrorCode.CONFIG_PARSE_ERROR, List.of(diagnostic));

        List<Diagnostic> diagnosticsView = exception.diagnostics();
        assertThatThrownBy(() -> diagnosticsView.add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(diagnosticsView::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesCause() {
        RuntimeException cause = new RuntimeException("boom");
        PlatformException exception = new PlatformException(
                PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of(), cause);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void rejectsNullCodeAndNullDiagnostics() {
        List<Diagnostic> noDiagnostics = List.of();
        assertThatThrownBy(() -> new PlatformException(null, noDiagnostics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlatformException(PlatformErrorCode.CONFIG_PARSE_ERROR, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void messageIsStableAndDerivedFromCodeOnly() {
        Diagnostic diagnostic = new Diagnostic(
                PlatformErrorCode.PROVIDER_CONFIG_ERROR,
                Severity.ERROR,
                SPEC_PROVIDERS_0,
                "failed to load provider",
                null);
        PlatformException exception =
                new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, List.of(diagnostic));

        assertThat(exception.getMessage())
                .isEqualTo("Platform error PROVIDER_CONFIG_ERROR with 1 diagnostic(s)");
    }

    @Test
    void messageNeverLeaksBusinessValues() {
        Diagnostic diagnostic = new Diagnostic(
                PlatformErrorCode.PROVIDER_CONFIG_ERROR,
                Severity.ERROR,
                SPEC_PROVIDERS_0,
                "failed to load provider secret-token for orders",
                null);
        PlatformException exception =
                new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, List.of(diagnostic));

        assertThat(exception.getMessage())
                .doesNotContain("secret-token")
                .doesNotContain("orders")
                .doesNotContain(SPEC_PROVIDERS_0);
    }
}
