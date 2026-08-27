package dev.hogwai.platform.examples.provider.exceptions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.hogwai.platform.examples.provider.model.SupplyChainData;
import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.execution.CancellationToken;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplyChainExceptionDetectorFactoryTest {

    private static final ProviderVersion VERSION = ProviderVersion.parse("1.0.0");
    private static final DataAccessFactory DATA_ACCESS_FACTORY = _ -> new DataAccess() {
        @Override
        public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
            return List.of();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Schema schema,
                                                  Map<String, String> columnByField) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Map<String, ?> parameters,
                                                  Schema schema,
                                                  Map<String, String> columnByField) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Schema schema,
                                                  Map<String, String> columnByField,
                                                  DataSetLimits limits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Map<String, ?> parameters,
                                                  Schema schema,
                                                  Map<String, String> columnByField,
                                                  DataSetLimits limits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingDataSet streamQuery(QueryContext context,
                                            String operation,
                                            String sql,
                                            Schema schema,
                                            Map<String, String> columnByField,
                                            DataSetLimits limits,
                                            int batchSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(QueryContext context,
                           String operation,
                           String sql,
                           Map<String, ?> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no resources
        }
    };
    private static final BuildContext BUILD_CONTEXT = new BuildContext(
            Clock.systemUTC(), _ -> {
    }, DATA_ACCESS_FACTORY);

    @Test
    void exposesTheExactDetectorDescriptor() {
        ProviderDescriptor descriptor = new SupplyChainExceptionDetectorFactory().descriptor();

        assertThat(descriptor.providerId().value()).isEqualTo("supply-chain.exception-detector");
        assertThat(descriptor.version()).isEqualTo(VERSION);
        assertThat(descriptor.capabilityKind()).isEqualTo(CapabilityKind.TRANSFORM);
        assertThat(descriptor.spiMajor()).isEqualTo(SpiMajor.V1);
        assertThat(descriptor.inputPorts().keySet()).extracting(PortId::value)
                .containsExactlyInAnyOrder("orders", "deliveries");
        assertThat(descriptor.outputPorts().keySet()).isEqualTo(Set.of(new PortId("records")));
        assertThat(descriptor.outputPorts().get(new PortId("records")).schema().fields())
                .extracting(field -> field.id().value())
                .containsExactly("orderId", "exceptionType", "severity", "reason", "recommendedAction");
    }

    @Test
    void detectorProducesAllExceptionKindsAndLeavesOneOrderClean() {
        MaterializedDataSet orders = testOrders();
        MaterializedDataSet deliveries = testDeliveries();
        CapabilityInstance detector = detector(Map.of(
                "lateToleranceDays", 1L,
                "minimumDeliveryRatio", new BigDecimal("0.80"),
                "priorityRiskDays", 3L));

        var result = (MaterializedDataSet) detector.execute(CapabilityInputs.of(Map.of(
                new PortId("orders"), orders,
                new PortId("deliveries"), deliveries)), context());

        assertThat(result.records())
                .extracting(schemaRecord -> schemaRecord.value(new FieldId("exceptionType")))
                .contains("LATE_DELIVERY", "INSUFFICIENT_QUANTITY", "PRIORITY_RISK");
        assertThat(result.records()).allSatisfy(schemaRecord -> {
            assertThat(schemaRecord.value(new FieldId("orderId"))).isNotNull();
            assertThat(schemaRecord.value(new FieldId("reason"))).asString().isNotBlank();
            assertThat(schemaRecord.value(new FieldId("recommendedAction"))).asString().isNotBlank();
        });
        assertThat(result.records())
                .noneMatch(schemaRecord -> "OK-001".equals(schemaRecord.value(new FieldId("orderId"))));
    }

    @Test
    void detectorRejectsMissingAndInvalidConfigurationWithDiagnostics() {
        List<Map<String, Object>> invalid = List.of(
                Map.of(),
                Map.of("lateToleranceDays", -1L, "minimumDeliveryRatio", new BigDecimal("0.8"),
                        "priorityRiskDays", 3L),
                Map.of("lateToleranceDays", 1L, "minimumDeliveryRatio", new BigDecimal("1.1"),
                        "priorityRiskDays", 3L),
                Map.of("lateToleranceDays", 1L, "minimumDeliveryRatio", new BigDecimal("0.8"),
                        "priorityRiskDays", -1L));

        for (Map<String, Object> config : invalid) {
            assertThat(new SupplyChainExceptionDetectorFactory().validate(config))
                    .isNotEmpty()
                    .allSatisfy(diagnostic -> assertThat(diagnostic.code())
                            .isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR));
        }
    }

    @Test
    void detectorIsStableAndHonoursCancellationAndInvalidInputs() {
        MaterializedDataSet orders = testOrders();
        MaterializedDataSet deliveries = testDeliveries();
        Map<PortId, MaterializedDataSet> inputs = Map.of(
                new PortId("orders"), orders, new PortId("deliveries"), deliveries);
        CapabilityInstance detector = detector(Map.of(
                "lateToleranceDays", 1L,
                "minimumDeliveryRatio", new BigDecimal("0.8"),
                "priorityRiskDays", 3L));

        MaterializedDataSet first = (MaterializedDataSet) detector.execute(
                CapabilityInputs.of(inputs), context());
        assertThat(first.records()).extracting(SchemaRecord::values)
                .isEqualTo(((MaterializedDataSet) detector.execute(
                        CapabilityInputs.of(inputs), context())).records().stream()
                        .map(SchemaRecord::values).toList());
        var capInputs = CapabilityInputs.of(inputs);
        var cancelledContext = cancelledContext();
        assertThatThrownBy(() -> detector.execute(capInputs, cancelledContext))
                .isInstanceOf(PlatformException.class)
                .extracting("code").isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED);
        var emptyInputs = CapabilityInputs.of(Map.of());
        var context = context();
        assertThatThrownBy(() -> detector.execute(emptyInputs, context))
                .isInstanceOf(PlatformException.class);
    }

    private static ExecutionContext cancelledContext() {
        return context(() -> true, Instant.parse("2099-01-01T00:00:00Z"));
    }

    private static CapabilityInstance detector(Map<String, Object> config) {
        return new SupplyChainExceptionDetectorFactory().create(config, BUILD_CONTEXT);
    }

    private static MaterializedDataSet testOrders() {
        Schema schema = SupplyChainSchemas.orders();
        return SupplyChainData.dataSet(schema, "test-orders", List.of(
                SupplyChainData.schemaRecord(schema, Map.of("orderId", "LATE-001", "orderedQuantity", 10L,
                        "requiredAt", Instant.parse("2025-01-10T00:00:00Z"), "priority", "NORMAL")),
                SupplyChainData.schemaRecord(schema, Map.of("orderId", "SHORT-001", "orderedQuantity", 100L,
                        "requiredAt", Instant.parse("2025-01-20T00:00:00Z"), "priority", "HIGH")),
                SupplyChainData.schemaRecord(schema, Map.of("orderId", "OK-001", "orderedQuantity", 20L,
                        "requiredAt", Instant.parse("2025-01-15T00:00:00Z"), "priority", "NORMAL"))));
    }

    private static MaterializedDataSet testDeliveries() {
        Schema schema = SupplyChainSchemas.deliveries();
        return SupplyChainData.dataSet(schema, "test-deliveries", List.of(
                SupplyChainData.schemaRecord(schema, Map.of("orderId", "LATE-001", "deliveredQuantity", 10L,
                        "deliveredAt", Instant.parse("2025-01-12T00:00:00Z"))),
                SupplyChainData.schemaRecord(schema, Map.of("orderId", "SHORT-001", "deliveredQuantity", 50L,
                        "deliveredAt", Instant.parse("2025-01-19T00:00:00Z"))),
                SupplyChainData.schemaRecord(schema, Map.of("orderId", "OK-001", "deliveredQuantity", 20L,
                        "deliveredAt", Instant.parse("2025-01-14T00:00:00Z")))));
    }

    private static ExecutionContext context() {
        return context(() -> false, Instant.parse("2099-01-01T00:00:00Z"));
    }

    private static ExecutionContext context(CancellationToken token, Instant deadline) {
        return new ExecutionContext("request-1", "snapshot-1", deadline, token, "correlation-1");
    }
}
