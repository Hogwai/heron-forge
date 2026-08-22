package dev.hogwai.platform.spi;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantsTest {

    @Test
    void spiMajorV1IsOne() {
        assertThat(SpiMajor.V1).isEqualTo(1);
    }

    @Test
    void capabilityKindHasExactlySourceAndTransform() {
        assertThat(CapabilityKind.values()).containsExactly(CapabilityKind.SOURCE, CapabilityKind.TRANSFORM);
    }

    @Test
    void severityHasExactlyInfoWarningError() {
        assertThat(Severity.values())
                .containsExactly(Severity.INFO, Severity.WARNING, Severity.ERROR);
    }

    @Test
    void platformErrorCodeHasExactlyTheTwelveStableConstants() {
        assertThat(PlatformErrorCode.values()).containsExactly(
                PlatformErrorCode.CONFIG_PARSE_ERROR,
                PlatformErrorCode.CONFIG_SCHEMA_ERROR,
                PlatformErrorCode.PROVIDER_NOT_FOUND,
                PlatformErrorCode.PROVIDER_VERSION_MISMATCH,
                PlatformErrorCode.PROVIDER_CONFIG_ERROR,
                PlatformErrorCode.GRAPH_REFERENCE_ERROR,
                PlatformErrorCode.GRAPH_CYCLE_ERROR,
                PlatformErrorCode.SCHEMA_INCOMPATIBLE,
                PlatformErrorCode.DATASET_LIMIT_EXCEEDED,
                PlatformErrorCode.CAPABILITY_EXECUTION_ERROR,
                PlatformErrorCode.DEADLINE_EXCEEDED,
                PlatformErrorCode.CANCELLATION_REQUESTED,
                PlatformErrorCode.DATA_ACCESS_UNAVAILABLE);
    }
}
