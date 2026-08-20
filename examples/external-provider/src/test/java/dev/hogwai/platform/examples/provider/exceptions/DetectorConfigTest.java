package dev.hogwai.platform.examples.provider.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DetectorConfigTest {

    @Test
    void parsesImmutableDetectorThresholds() {
        DetectorConfig config = DetectorConfig.from(Map.of(
                "lateToleranceDays", 2L,
                "minimumDeliveryRatio", new BigDecimal("0.80"),
                "priorityRiskDays", 5L));

        assertThat(config.lateToleranceDays()).isEqualTo(2L);
        assertThat(config.minimumDeliveryRatio()).isEqualByComparingTo("0.80");
        assertThat(config.priorityRiskDays()).isEqualTo(5L);
        assertThat(DetectorConfig.configurationSchema().requiredFields())
                .containsExactlyInAnyOrder("lateToleranceDays", "minimumDeliveryRatio", "priorityRiskDays");
    }

    @Test
    void validatesAllThreeThresholdsThroughTheConfigType() {
        assertThat(DetectorConfig.validate(Map.of())).hasSize(3);
        assertThat(DetectorConfig.validate(Map.of(
                "lateToleranceDays", -1L,
                "minimumDeliveryRatio", new BigDecimal("1.1"),
                "priorityRiskDays", -1L))).hasSize(3);
    }
}
