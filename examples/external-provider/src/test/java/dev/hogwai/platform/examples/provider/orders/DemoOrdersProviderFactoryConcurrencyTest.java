package dev.hogwai.platform.examples.provider.orders;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import dev.hogwai.platform.examples.provider.support.FakeDataAccessSupport;
import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataRow;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic concurrency check: one shared {@link CapabilityInstance} created
 * by {@link DemoOrdersProviderFactory#create} is executed by two threads at the
 * same time (start latch plus an overlap barrier inside the fake data access).
 */
class DemoOrdersProviderFactoryConcurrencyTest {

    private static final int THREADS = 2;

    @Test
    void executesOneSharedInstanceConcurrentlyWithCorrectUncorruptedResults() throws Exception {
        ConcurrentDataAccess access = new ConcurrentDataAccess(List.of(
                Map.of("order_id", "ORDER-001", "ordered_quantity", 12L,
                        "required_at", Instant.parse("2025-01-01T00:00:00Z"), "priority", "HIGH"),
                Map.of("order_id", "ORDER-002", "ordered_quantity", 7L,
                        "required_at", Instant.parse("2025-01-02T00:00:00Z"), "priority", "NORMAL")));
        BuildContext buildContext = new BuildContext(Clock.systemUTC(), _ -> {
        }, _ -> access);
        CapabilityInstance instance = new DemoOrdersProviderFactory().create(
                Map.of("url", "jdbc:postgresql://localhost:5432/heron_demo",
                        "user", "test-user", "password", "test-password"), buildContext);
        ExecutionContext executionContext = new ExecutionContext("r1", "snap-1",
                Instant.parse("2099-01-01T00:00:00Z"), () -> false, "c1");

        CountDownLatch start = new CountDownLatch(THREADS);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<DataSet>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    start.countDown();
                    start.await();
                    return instance.execute(CapabilityInputs.of(Map.of()), executionContext);
                }));
            }
            List<DataSet> results = new ArrayList<>();
            for (Future<DataSet> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(results).hasSize(THREADS);
            assertThat(results).allSatisfy(result -> {
                var materialized = result instanceof MaterializedDataSet m
                        ? m : ((StreamingDataSet) result).toMaterialized();
                assertThat(materialized.schema().fields())
                        .extracting(field -> field.id().value())
                        .containsExactly("orderId", "orderedQuantity", "requiredAt", "priority");
                assertThat(materialized.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderId")))
                        .containsExactly("ORDER-001", "ORDER-002");
                assertThat(materialized.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("orderedQuantity")))
                        .containsExactly(12L, 7L);
                assertThat(materialized.records()).extracting(schemaRecord -> schemaRecord.value(new FieldId("priority")))
                        .containsExactly("HIGH", "NORMAL");
            });
            // The overlap barrier only releases when both queries are active at the
            // same time, so both executions provably overlapped on the shared instance.
            assertThat(access.maxActiveQueries()).isEqualTo(THREADS);
            assertThat(access.totalQueries()).isEqualTo(THREADS);
        }
    }

    /**
     * Fake data access that counts concurrent queries and forces real overlap.
     */
    private static final class ConcurrentDataAccess implements DataAccess {
        private final List<Map<String, Object>> values;
        private final CyclicBarrier overlapBarrier = new CyclicBarrier(THREADS);
        private final AtomicInteger activeQueries = new AtomicInteger();
        private final AtomicInteger maxActiveQueries = new AtomicInteger();
        private final AtomicInteger totalQueries = new AtomicInteger();

        private ConcurrentDataAccess(List<Map<String, Object>> values) {
            this.values = values;
        }

        @Override
        public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
            totalQueries.incrementAndGet();
            int currentActive = activeQueries.incrementAndGet();
            maxActiveQueries.accumulateAndGet(currentActive, Math::max);
            try {
                overlapBarrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for concurrent queries", failure);
            } catch (BrokenBarrierException | TimeoutException failure) {
                throw new IllegalStateException("concurrent queries did not overlap", failure);
            } finally {
                activeQueries.decrementAndGet();
            }
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
                                                  Map<String, ?> parameters, Schema schema, Map<String, String> columnByField,
                                                  DataSetLimits limits) {
            QueryRequest<SchemaRecord> request = new QueryRequest<>(operation, sql, parameters,
                    row -> FakeDataAccessSupport.toRecord(row, schema, columnByField));
            List<SchemaRecord> records = query(request, context);
            return FakeDataAccessSupport.dataSet(schema, operation, records, limits);
        }

        @Override
        public StreamingDataSet streamQuery(QueryContext context,
                                            String operation,
                                            String sql,
                                            Schema schema,
                                            Map<String, String> columnByField,
                                            DataSetLimits limits,
                                            int batchSize) {
            MaterializedDataSet materialized = queryToDataSet(context, operation, sql,
                    schema, columnByField, limits);
            return StreamingDataSet.over(schema,
                    materialized.records().iterator(), limits, batchSize,
                    context.deadline(), context::isCancellationRequested);
        }

        @Override
        public int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no resources
        }

        private int maxActiveQueries() {
            return maxActiveQueries.get();
        }

        private int totalQueries() {
            return totalQueries.get();
        }
    }

    /**
     * Fake row backed by a plain map, used by the fake data access in this file.
     */
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
