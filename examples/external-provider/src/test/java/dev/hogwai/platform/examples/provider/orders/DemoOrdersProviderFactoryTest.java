package dev.hogwai.platform.examples.provider.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.DataRow;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoOrdersProviderFactoryTest {

    private static final ProviderVersion VERSION = ProviderVersion.parse("1.0.0");

    @Test
    void exposesOrdersDescriptorAndConfiguration() {
        ProviderDescriptor descriptor = new DemoOrdersProviderFactory().descriptor();

        assertThat(descriptor.providerId().value()).isEqualTo("demo.orders");
        assertThat(descriptor.version()).isEqualTo(VERSION);
        assertThat(descriptor.capabilityKind()).isEqualTo(CapabilityKind.SOURCE);
        assertThat(descriptor.spiMajor()).isEqualTo(SpiMajor.V1);
        assertThat(descriptor.inputPorts().keySet()).isEqualTo(Set.of());
        assertThat(descriptor.outputPorts().keySet()).isEqualTo(Set.of(new PortId("records")));
        assertThat(descriptor.outputPorts().get(new PortId("records")).schema().fields())
                .extracting(field -> field.id().value())
                .containsExactly("orderId", "orderedQuantity", "requiredAt", "priority");
        assertThat(new DemoOrdersProviderFactory().validate(validDatabaseConfig())).isEmpty();
    }

    @Test
    void rejectsConfigurationMissingRequiredDatabaseFields() {
        assertThat(new DemoOrdersProviderFactory().validate(Map.of()))
                .extracting(dev.hogwai.platform.spi.Diagnostic::message)
                .containsExactlyInAnyOrder(
                        "missing required database configuration field",
                        "missing required database configuration field",
                        "missing required database configuration field");
    }

    @Test
    void readsOrdersThroughDataAccessWithStableQueryContextAndOrder() {
        RecordingDataAccess access = new RecordingDataAccess(List.of(
                Map.of("order_id", "ORDER-001", "ordered_quantity", 12L,
                        "required_at", Instant.parse("2025-01-01T00:00:00Z"), "priority", "HIGH"),
                Map.of("order_id", "ORDER-002", "ordered_quantity", 7L,
                        "required_at", Instant.parse("2025-01-02T00:00:00Z"), "priority", "NORMAL")));
        QueryContext queryContext = new QueryContext(Instant.parse("2099-01-01T00:00:00Z"), () -> false);

        var first = OrdersQuery.read(access, queryContext, FakeDataAccessSupport.DEFAULT_LIMITS);
        var second = OrdersQuery.read(access, queryContext, FakeDataAccessSupport.DEFAULT_LIMITS);

        assertThat(access.requests()).hasSize(2);
        assertThat(access.requests()).allSatisfy(request -> {
            assertThat(request.operation()).isEqualTo("orders");
            assertThat(request.sql()).isEqualTo("SELECT order_id, ordered_quantity, required_at, priority "
                    + "FROM orders ORDER BY order_id");
            assertThat(request.parameters()).isEmpty();
        });
        assertThat(access.contexts()).containsExactly(queryContext, queryContext);
        assertThat(first.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderId")))
                .containsExactly("ORDER-001", "ORDER-002");
        assertThat(first.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderedQuantity")))
                .containsExactly(12L, 7L);
        assertThat(second.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderId")))
                .containsExactly("ORDER-001", "ORDER-002");
    }

    @Test
    void closesDataAccessWhenResourceRegistrationFails() {
        RecordingDataAccess access = new RecordingDataAccess(List.of());
        RuntimeException registrationFailure = new IllegalStateException("registration failed");
        DataAccessFactory factory = configuration -> access;
        BuildContext context = new BuildContext(java.time.Clock.systemUTC(),
                resource -> {
                    throw registrationFailure;
                }, factory);

        var failingFactory = new DemoOrdersProviderFactory();
        assertThatThrownBy(() -> failingFactory.create(
                Map.of("url", "jdbc:postgresql://localhost:5432/heron_demo",
                        "user", "test-user", "password", "test-password"), context))
                .isSameAs(registrationFailure);
        assertThat(access.closed()).isTrue();
    }

    private static Map<String, Object> validDatabaseConfig() {
        return Map.of("url", "jdbc:postgresql://localhost:5432/heron_demo",
                "user", "test-user", "password", "test-password");
    }

    private static final class RecordingDataAccess implements DataAccess {
        private final List<Map<String, Object>> values;
        private final List<QueryRequest<?>> requests = new ArrayList<>();
        private final List<QueryContext> contexts = new ArrayList<>();
        private boolean closed;

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
                                                  Map<String, ?> parameters, Schema schema,
                                                  Map<String, String> columnByField, DataSetLimits limits) {
            QueryRequest<SchemaRecord> request = new QueryRequest<>(operation, sql, parameters,
                    row -> FakeDataAccessSupport.toRecord(row, schema, columnByField));
            List<SchemaRecord> records = query(request, context);
            return FakeDataAccessSupport.dataSet(schema, operation, records, limits);
        }

        @Override
        public StreamingDataSet streamQuery(QueryContext context, String operation, String sql,
                                            Schema schema, Map<String, String> columnByField,
                                            DataSetLimits limits, int batchSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed = true;
        }

        private List<QueryRequest<?>> requests() {
            return requests;
        }

        private List<QueryContext> contexts() {
            return contexts;
        }

        private boolean closed() {
            return closed;
        }
    }

    private record MapDataRow(Map<String, Object> values) implements DataRow {

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
