package dev.hogwai.platform.examples.provider.exceptions;

import dev.hogwai.platform.examples.provider.model.SupplyChainData;
import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionDetectorTest {

    private static final Instant NOW = Instant.parse("2025-01-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void emitsPriorityRiskForHighPriorityOrderWithPastDueDate() {
        assertThat(exceptionTypes("HIGH", Instant.parse("2025-01-19T00:00:00Z")))
                .contains("PRIORITY_RISK");
    }

    @Test
    void emitsPriorityRiskForHighPriorityOrderInsideTheConfiguredWindow() {
        assertThat(exceptionTypes("HIGH", Instant.parse("2025-01-22T00:00:00Z")))
                .contains("PRIORITY_RISK");
    }

    @Test
    void doesNotEmitPriorityRiskForHighPriorityOrderOutsideTheConfiguredWindow() {
        assertThat(exceptionTypes("HIGH", Instant.parse("2025-01-24T00:00:00Z")))
                .doesNotContain("PRIORITY_RISK")
                .contains("INSUFFICIENT_QUANTITY");
    }

    @Test
    void doesNotEmitPriorityRiskForNormalOrderInsideTheConfiguredWindow() {
        assertThat(exceptionTypes("NORMAL", Instant.parse("2025-01-22T00:00:00Z")))
                .doesNotContain("PRIORITY_RISK")
                .contains("INSUFFICIENT_QUANTITY");
    }

    private static List<String> exceptionTypes(String priority, Instant requiredAt) {
        DetectorConfig config = DetectorConfig.from(Map.of(
                "lateToleranceDays", 1L,
                "minimumDeliveryRatio", new BigDecimal("0.80"),
                "priorityRiskDays", 3L));
        ExceptionDetector detector = new ExceptionDetector(config, CLOCK);
        MaterializedDataSet orders = SupplyChainData.dataSet(SupplyChainSchemas.orders(), "test-orders", List.of(
                SupplyChainData.schemaRecord(SupplyChainSchemas.orders(), Map.of(
                        "orderId", "ORDER-001", "orderedQuantity", 100L,
                        "requiredAt", requiredAt, "priority", priority))));
        MaterializedDataSet deliveries = SupplyChainData.dataSet(SupplyChainSchemas.deliveries(), "test-deliveries", List.of(
                SupplyChainData.schemaRecord(SupplyChainSchemas.deliveries(), Map.of(
                        "orderId", "ORDER-001", "deliveredQuantity", 50L,
                        "deliveredAt", requiredAt))));

        return detector.execute(CapabilityInputs.of(Map.of(
                        new PortId("orders"), orders,
                        new PortId("deliveries"), deliveries)), context()).records().stream()
                .map(schemaRecord -> (String) schemaRecord.value(new FieldId("exceptionType")))
                .toList();
    }

    private static ExecutionContext context() {
        return new ExecutionContext("detector-test", "snapshot-1", Instant.parse("2099-01-01T00:00:00Z"),
                () -> false, "detector-correlation");
    }
}
