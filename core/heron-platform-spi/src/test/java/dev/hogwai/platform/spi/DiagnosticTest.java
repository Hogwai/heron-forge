package dev.hogwai.platform.spi;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticTest {

    public static final String MESSAGE = "message";

    @Test
    void diagnosticExposesAllFields() {
        Diagnostic diagnostic = new Diagnostic(
                PlatformErrorCode.CONFIG_PARSE_ERROR,
                Severity.ERROR,
                "/spec/capabilities/1/inputs/orders",
                "cannot parse input",
                "check the input schema");

        assertThat(diagnostic.code()).isEqualTo(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(diagnostic.severity()).isEqualTo(Severity.ERROR);
        assertThat(diagnostic.path()).isEqualTo("/spec/capabilities/1/inputs/orders");
        assertThat(diagnostic.message()).isEqualTo("cannot parse input");
        assertThat(diagnostic.remediation()).isEqualTo("check the input schema");
    }

    @Test
    void diagnosticAllowsNullPathAndRemediation() {
        Diagnostic diagnostic =
                new Diagnostic(PlatformErrorCode.CONFIG_SCHEMA_ERROR, Severity.WARNING, null, MESSAGE, null);

        assertThat(diagnostic.path()).isNull();
        assertThat(diagnostic.remediation()).isNull();
    }

    @Test
    void diagnosticRejectsNullCode() {
        assertThatThrownBy(() -> new Diagnostic(null, Severity.ERROR, null, MESSAGE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void diagnosticRejectsNullSeverity() {
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, null, null, MESSAGE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void diagnosticRejectsNullOrBlankMessage() {
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void diagnosticRejectsBlankPathWhenPresent() {
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, "", MESSAGE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, "   ", MESSAGE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void diagnosticRejectsBlankRemediationWhenPresent() {
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, MESSAGE, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, MESSAGE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void diagnosticIsAValueObject() {
        Diagnostic a = new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, MESSAGE, null);
        Diagnostic b = new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, MESSAGE, null);
        Diagnostic c = new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null, "other", null);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
        assertThat(a.toString()).contains(MESSAGE);
    }
}
