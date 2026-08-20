package dev.hogwai.platform.examples.provider.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.data.postgres.PostgresJdbiDataAccessFactory;
import dev.hogwai.platform.examples.provider.deliveries.DemoDeliveriesProviderFactory;
import dev.hogwai.platform.examples.provider.exceptions.SupplyChainExceptionDetectorFactory;
import dev.hogwai.platform.examples.provider.orders.DemoOrdersProviderFactory;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Real PostgreSQL integration test, opt-in because it requires Compose. */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class SupplyChainPostgresIntegrationTest {

    private static final Instant DEADLINE = Instant.parse("2099-01-01T00:00:00Z");
    private static final DataAccessFactory DATA_ACCESS_FACTORY = configuration ->
            new TrackingDataAccess(new PostgresJdbiDataAccessFactory().open(configuration));

    @Test
    void readsInitializedRowsAndRunsAllThreeProviders() {
        List<AutoCloseable> resources = new ArrayList<>();
        BuildContext buildContext = new BuildContext(Clock.systemUTC(), resources::add, DATA_ACCESS_FACTORY);
        try {
            CapabilityInstance ordersProvider = new DemoOrdersProviderFactory().create(Map.of(), buildContext);
            CapabilityInstance deliveriesProvider = new DemoDeliveriesProviderFactory().create(Map.of(), buildContext);
            MaterializedDataSet orders = ordersProvider.execute(CapabilityInputs.of(Map.of()), context());
            MaterializedDataSet deliveries = deliveriesProvider.execute(CapabilityInputs.of(Map.of()), context());

            assertThat(orders.rowCount()).isEqualTo(3);
            assertThat(deliveries.rowCount()).isEqualTo(5);
            assertThat(orders.records()).extracting(record -> record.value(new FieldId("orderId")))
                    .containsExactly("LATE-001", "OK-001", "SHORT-001");
            assertThat(deliveries.records()).extracting(record -> record.value(new FieldId("orderId")))
                    .contains("LATE-001", "SHORT-001", "OK-001");

            CapabilityInstance detector = new SupplyChainExceptionDetectorFactory().create(Map.of(
                    "lateToleranceDays", 1L,
                    "minimumDeliveryRatio", new BigDecimal("0.80"),
                    "priorityRiskDays", 3L), buildContext);
            MaterializedDataSet exceptions = detector.execute(CapabilityInputs.of(Map.of(
                    new PortId("orders"), orders,
                    new PortId("deliveries"), deliveries)), context());

            assertThat(exceptions.records()).extracting(record -> record.value(new FieldId("exceptionType")))
                    .contains("LATE_DELIVERY", "INSUFFICIENT_QUANTITY", "PRIORITY_RISK");
            assertThat(exceptions.records()).noneMatch(record -> "OK-001"
                    .equals(record.value(new FieldId("orderId"))));
        } finally {
            closeInReverseOrder(resources);
        }
    }

    @Test
    void closesRegisteredDataAccessWhenFollowingProviderCreationFails() {
        List<AutoCloseable> resources = new ArrayList<>();
        BuildContext buildContext = new BuildContext(Clock.systemUTC(), resources::add, DATA_ACCESS_FACTORY);
        try {
            new DemoOrdersProviderFactory().create(Map.of(), buildContext);
            assertThatThrownBy(() -> new SupplyChainExceptionDetectorFactory().create(Map.of(), buildContext))
                    .isInstanceOf(PlatformException.class);
        } finally {
            closeInReverseOrder(resources);
        }
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0)).isInstanceOf(TrackingDataAccess.class);
        assertThat(((TrackingDataAccess) resources.get(0)).closed()).isTrue();
    }

    @Test
    void sanitizesStartupProbeFailures() {
        assertThatThrownBy(() -> DATA_ACCESS_FACTORY.open(new DataAccessConfiguration(
                "jdbc:postgresql://127.0.0.1:1/unavailable", "probe-user", "probe-password")))
                .isInstanceOf(PlatformException.class)
                .satisfies(failure -> {
                    PlatformException exception = (PlatformException) failure;
                    assertThat(exception.getMessage()).doesNotContain("probe-password", "127.0.0.1");
                    assertThat(exception.diagnostics()).extracting(diagnostic -> diagnostic.message())
                            .allSatisfy(message -> assertThat(message).doesNotContain("probe-password", "127.0.0.1"));
                });
    }

    private static ExecutionContext context() {
        return new ExecutionContext("postgres-request", "postgres-snapshot", DEADLINE,
                () -> false, "postgres-correlation");
    }

    private static final class TrackingDataAccess implements DataAccess {
        private final DataAccess delegate;
        private boolean closed;

        private TrackingDataAccess(DataAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
            return delegate.query(request, context);
        }

        @Override
        public void close() {
            closed = true;
            delegate.close();
        }

        private boolean closed() {
            return closed;
        }
    }

    private static void closeInReverseOrder(List<AutoCloseable> resources) {
        RuntimeException failure = null;
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Exception closeFailure) {
                if (failure == null) {
                    failure = new RuntimeException("PostgreSQL test resource cleanup failed", closeFailure);
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
