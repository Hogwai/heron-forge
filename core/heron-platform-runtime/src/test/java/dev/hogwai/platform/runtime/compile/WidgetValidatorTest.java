package dev.hogwai.platform.runtime.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import dev.hogwai.platform.runtime.config.WidgetConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import org.junit.jupiter.api.Test;

class WidgetValidatorTest {

    @Test
    void passesWhenTargetsReferenceDeclaredEndpoints() {
        List<Diagnostic> diagnostics = WidgetValidator.validate(
                Set.of("e1", "e2"),
                List.of(new WidgetConfig("w1", "kpi", "T", "e1")));
        assertThat(diagnostics).isEmpty();
    }

    @Test
    void reportsDanglingTargetWithStablePath() {
        List<Diagnostic> diagnostics = WidgetValidator.validate(
                Set.of("e1"),
                List.of(new WidgetConfig("w1", "kpi", "T", "missing")));
        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
            assertThat(diagnostic.path()).isEqualTo("/widgets/0/target");
        });
    }

    @Test
    void emptyWidgetsYieldNoDiagnostics() {
        assertThat(WidgetValidator.validate(Set.of(), List.of())).isEmpty();
    }
}
