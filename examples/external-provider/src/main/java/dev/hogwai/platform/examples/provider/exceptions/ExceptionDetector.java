package dev.hogwai.platform.examples.provider.exceptions;

import dev.hogwai.platform.examples.provider.model.SupplyChainData;
import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.examples.provider.support.ExecutionSupport;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, synchronous supply-chain exception detection engine.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class ExceptionDetector implements CapabilityInstance {

    public static final String ORDER_ID = "orderId";
    private final DetectorConfig config;
    private final Clock clock;

    ExceptionDetector(DetectorConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
        ExecutionSupport.checkExecution(context);
        Objects.requireNonNull(inputs, "inputs must not be null");
        List<SchemaRecord> orders = requireRecords(inputs.get(new PortId("orders")), SupplyChainSchemas.orders());
        List<SchemaRecord> deliveries = requireRecords(inputs.get(new PortId("deliveries")), SupplyChainSchemas.deliveries());
        Map<String, List<SchemaRecord>> deliveriesByOrder = groupDeliveries(deliveries, context);
        List<SchemaRecord> exceptions = new ArrayList<>();
        Instant now = clock.instant();
        for (SchemaRecord order : orders) {
            ExecutionSupport.checkExecution(context);
            evaluateOrder(order, deliveriesByOrder, exceptions, now);
        }
        return SupplyChainData.dataSet(SupplyChainSchemas.exceptions(), "supply-chain-exceptions", exceptions);
    }

    private Map<String, List<SchemaRecord>> groupDeliveries(List<SchemaRecord> deliveries, ExecutionContext context) {
        Map<String, List<SchemaRecord>> grouped = new LinkedHashMap<>();
        for (SchemaRecord delivery : deliveries) {
            ExecutionSupport.checkExecution(context);
            grouped.computeIfAbsent(SupplyChainData.string(delivery, ORDER_ID), ignored -> new ArrayList<>())
                    .add(delivery);
        }
        return grouped;
    }

    private static List<SchemaRecord> requireRecords(MaterializedDataSet dataSet, Schema schema) {
        if (dataSet == null || !dataSet.schema().equals(schema)) {
            throw new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of());
        }
        return new ArrayList<>(dataSet.records());
    }

    private void evaluateOrder(SchemaRecord order, Map<String, List<SchemaRecord>> deliveries,
                               List<SchemaRecord> exceptions, Instant now) {
        String orderId = SupplyChainData.string(order, ORDER_ID);
        long ordered = SupplyChainData.longValue(order, "orderedQuantity");
        Instant requiredAt = SupplyChainData.instant(order, "requiredAt");
        List<SchemaRecord> orderDeliveries = deliveries.getOrDefault(orderId, List.of());
        long delivered = orderDeliveries.stream()
                .mapToLong(delivery -> SupplyChainData.longValue(delivery, "deliveredQuantity"))
                .sum();
        Instant latestDelivery = orderDeliveries.stream()
                .map(delivery -> SupplyChainData.instant(delivery, "deliveredAt"))
                .max(Instant::compareTo).orElse(null);
        boolean late = latestDelivery == null
                || latestDelivery.isAfter(requiredAt.plus(config.lateToleranceDays(), ChronoUnit.DAYS));
        boolean shortage = BigDecimal.valueOf(delivered)
                .compareTo(BigDecimal.valueOf(ordered).multiply(config.minimumDeliveryRatio())) < 0;
        boolean highPriority = "HIGH".equalsIgnoreCase(SupplyChainData.string(order, "priority"));
        if (late) {
            exceptions.add(exception(orderId, "LATE_DELIVERY", "ERROR",
                    "Latest delivery is after the required date plus the configured tolerance.",
                    "Expedite the shipment and review the delivery commitment."));
        }
        if (shortage) {
            exceptions.add(exception(orderId, "INSUFFICIENT_QUANTITY", "ERROR",
                    "Delivered quantity is below the configured minimum delivery ratio.",
                    "Arrange the missing quantity or approve an allocation change."));
        }
        if (highPriority && (late || shortage) && isInPriorityWindow(requiredAt, now)) {
            exceptions.add(exception(orderId, "PRIORITY_RISK", "CRITICAL",
                    "High-priority order has a delivery risk within the " + config.priorityRiskDays()
                            + "-day risk policy.",
                    "Escalate the order and assign immediate fulfillment ownership."));
        }
    }

    private boolean isInPriorityWindow(Instant requiredAt, Instant now) {
        if (!requiredAt.isAfter(now)) {
            return true;
        }
        try {
            return !requiredAt.isAfter(now.plus(config.priorityRiskDays(), ChronoUnit.DAYS));
        } catch (ArithmeticException | DateTimeException _) {
            return true;
        }
    }

    private SchemaRecord exception(String orderId, String type, String severity, String reason, String action) {
        return SupplyChainData.schemaRecord(SupplyChainSchemas.exceptions(), Map.of(
                ORDER_ID, orderId, "exceptionType", type, "severity", severity,
                "reason", reason, "recommendedAction", action));
    }
}
