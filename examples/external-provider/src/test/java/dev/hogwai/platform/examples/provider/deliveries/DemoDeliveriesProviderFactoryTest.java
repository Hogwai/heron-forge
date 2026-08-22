package dev.hogwai.platform.examples.provider.deliveries;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.examples.provider.support.FakeDataAccessSupport;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataRow;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDeliveriesProviderFactoryTest {

    @Test
    void exposesDeliveriesDescriptorAndConfiguration() {
        var factory = new DemoDeliveriesProviderFactory();
        assertThat(factory.descriptor().providerId().value()).isEqualTo("demo.deliveries");
        assertThat(factory.descriptor().version()).isEqualTo(ProviderVersion.parse("1.0.0"));
        assertThat(factory.descriptor().capabilityKind()).isEqualTo(CapabilityKind.SOURCE);
        assertThat(factory.descriptor().spiMajor()).isEqualTo(SpiMajor.V1);
        assertThat(factory.descriptor().inputPorts().keySet()).isEqualTo(Set.of());
        assertThat(factory.descriptor().outputPorts().keySet()).isEqualTo(Set.of(new PortId("records")));
        assertThat(factory.descriptor().outputPorts().get(new PortId("records")).schema())
                .isSameAs(SupplyChainSchemas.deliveries());
        assertThat(factory.descriptor().outputPorts().get(new PortId("records")).schema().fields())
                .extracting(field -> field.id().value())
                .containsExactly("orderId", "deliveredQuantity", "deliveredAt");
        assertThat(factory.validate(Map.of(
                "url", "jdbc:postgresql://db/heron_demo", "user", "db-user", "password", "db-password"))).isEmpty();
        assertThat(factory.validate(Map.of("url", "jdbc:postgresql://db/heron_demo")))
                .extracting(dev.hogwai.platform.spi.Diagnostic::message)
                .containsExactlyInAnyOrder(
                        "missing required database configuration field",
                        "missing required database configuration field");
    }

    @Test
    void readsDeliveriesThroughDataAccessWithStableQueryContextAndOrder() {
        RecordingDataAccess access = new RecordingDataAccess(List.of(
                Map.of("order_id", "ORDER-001", "delivered_quantity", 5L,
                        "delivered_at", Instant.parse("2025-01-01T00:00:00Z")),
                Map.of("order_id", "ORDER-001", "delivered_quantity", 7L,
                        "delivered_at", Instant.parse("2025-01-02T00:00:00Z")),
                Map.of("order_id", "ORDER-002", "delivered_quantity", 3L,
                        "delivered_at", Instant.parse("2025-01-03T00:00:00Z"))));
        QueryContext queryContext = new QueryContext(Instant.parse("2099-01-01T00:00:00Z"), () -> false);

        var first = DeliveriesQuery.read(access, queryContext, FakeDataAccessSupport.DEFAULT_LIMITS);
        var second = DeliveriesQuery.read(access, queryContext, FakeDataAccessSupport.DEFAULT_LIMITS);

        assertThat(access.requests()).hasSize(2);
        assertThat(access.requests()).allSatisfy(request -> {
            assertThat(request.operation()).isEqualTo("deliveries");
            assertThat(request.sql()).isEqualTo("SELECT order_id, delivered_quantity, delivered_at "
                    + "FROM deliveries ORDER BY order_id, delivered_at, delivery_id");
            assertThat(request.parameters()).isEmpty();
        });
        assertThat(access.contexts()).containsExactly(queryContext, queryContext);
        assertThat(first.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderId")))
                .containsExactly("ORDER-001", "ORDER-001", "ORDER-002");
        assertThat(first.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("deliveredQuantity")))
                .containsExactly(5L, 7L, 3L);
        assertThat(second.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderId")))
                .containsExactly("ORDER-001", "ORDER-001", "ORDER-002");
    }

    private static final class RecordingDataAccess implements DataAccess {
        private final List<Map<String, Object>> values;
        private final List<QueryRequest<?>> requests = new ArrayList<>();
        private final List<QueryContext> contexts = new ArrayList<>();

        private RecordingDataAccess(List<Map<String, Object>> values) {
            this.values = values;
        }

        @Override
        public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
            requests.add(request);
            contexts.add(context);
            return values.stream().map(value -> request.mapper().map(new MapDataRow(value))).toList();
        }
        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
                Schema schema, Map<String, String> columnByField) {
            return queryToDataSet(context, operation, sql, Map.of(), schema, columnByField,
                    FakeDataAccessSupport.DEFAULT_LIMITS);
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
                Schema schema, Map<String, String> columnByField, DataSetLimits limits) {
            return queryToDataSet(context, operation, sql, Map.of(), schema, columnByField, limits);
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
                Map<String, ?> parameters, Schema schema, Map<String, String> columnByField) {
            return queryToDataSet(context, operation, sql, parameters, schema, columnByField,
                    FakeDataAccessSupport.DEFAULT_LIMITS);
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
                Map<String, ?> parameters, Schema schema, Map<String, String> columnByField, DataSetLimits limits) {
            QueryRequest<SchemaRecord> request = new QueryRequest<>(operation, sql, parameters,
                    row -> FakeDataAccessSupport.toRecord(row, schema, columnByField));
            List<SchemaRecord> records = query(request, context);
            return FakeDataAccessSupport.dataSet(schema, operation, records, limits);
        }

        @Override
        public int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no resources
        }

        private List<QueryRequest<?>> requests() {
            return requests;
        }

        private List<QueryContext> contexts() {
            return contexts;
        }
    }

    private static final class MapDataRow implements DataRow {
        private final Map<String, Object> values;

        private MapDataRow(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public String string(String column) {
            return (String) values.get(column);
        }

        @Override
        public long longValue(String column) {
            return ((Number) values.get(column)).longValue();
        }

        @Override
        public Instant instant(String column) {
            return (Instant) values.get(column);
        }
    }
}
