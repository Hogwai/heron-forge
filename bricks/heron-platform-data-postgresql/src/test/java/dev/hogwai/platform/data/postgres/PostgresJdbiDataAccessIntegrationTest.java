package dev.hogwai.platform.data.postgres;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.hogwai.platform.data.jdbi.JdbiDataAccessFactory;
import dev.hogwai.platform.data.jdbi.JdbiPoolOptions;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in PostgreSQL coverage for the PostgreSQL Jdbi data access implementation.
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class PostgresJdbiDataAccessIntegrationTest {

    private static final QueryContext ACTIVE = new QueryContext(Instant.parse("2099-01-01T00:00:00Z"), () -> false);
    private static final DataAccessConfiguration CONFIG = new DataAccessConfiguration(
            environmentOrDefault("HERON_DB_URL", "jdbc:postgresql://localhost:5432/heron_demo"),
            environmentOrDefault("HERON_DB_USER", "heron"),
            environmentOrDefault("HERON_DB_PASSWORD", "heron"));
    public static final String AMOUNT = "amount";
    public static final String VALUE = "value";

    @Test
    void opensWithProbeMapsRowsAndBindsNamedParameters() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            List<Row> rows = access.query(new QueryRequest<>("named-row", "SELECT :name AS name, :amount AS amount",
                            Map.of("name", "Ada", AMOUNT, 7L), row -> new Row(row.string("name"), row.longValue(AMOUNT))),
                    ACTIVE);

            assertThat(rows).containsExactly(new Row("Ada", 7L));
        }
    }

    @Test
    void mapsPostgresTimestamptzToInstant() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            List<Instant> instants = access.query(new QueryRequest<>("timestamp-row",
                    "SELECT TIMESTAMPTZ '2024-01-02 03:04:05+02' AS occurred_at", Map.of(),
                    row -> row.instant("occurred_at")), ACTIVE);

            assertThat(instants).containsExactly(Instant.parse("2024-01-02T01:04:05Z"));
        }
    }

    @Test
    void returnsAnEmptyMaterializedResult() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            List<String> values = access.query(new QueryRequest<>("empty-result",
                    "SELECT 'unused' AS value WHERE false", Map.of(), row -> row.string(VALUE)), ACTIVE);

            assertThat(values).isEmpty();
        }
    }

    @Test
    void translatesSqlNullAndInexactNumbersAsQueryFailures() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            QueryRequest<String> nullRequest = new QueryRequest<>("null-value", "SELECT NULL::text AS value", Map.of(),
                    row -> row.string(VALUE));
            QueryRequest<Long> fractionalRequest = new QueryRequest<>("fractional-value",
                    "SELECT 1.5::numeric AS value", Map.of(), row -> row.longValue(VALUE));
            QueryRequest<Long> overflowRequest = new QueryRequest<>("overflow-value",
                    "SELECT 9223372036854775808::numeric AS value", Map.of(), row -> row.longValue(VALUE));

            assertThatThrownBy(() -> access.query(nullRequest, ACTIVE)).isInstanceOf(PlatformException.class);
            assertThatThrownBy(() -> access.query(fractionalRequest, ACTIVE)).isInstanceOf(PlatformException.class);
            assertThatThrownBy(() -> access.query(overflowRequest, ACTIVE)).isInstanceOf(PlatformException.class);
        }
    }

    @Test
    void checksCancellationAndDeadlineBeforeOpeningAHandle() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            QueryRequest<Integer> request =
                    new QueryRequest<>("guarded-query", "SELECT 1", Map.of(), _ -> 1);
            var queryContext = new QueryContext(ACTIVE.deadline(), () -> true);
            assertThatThrownBy(() -> access.query(request, queryContext))
                    .isInstanceOf(PlatformException.class)
                    .extracting(exception -> ((PlatformException) exception).code())
                    .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED);
            var queryCtxDeadline = new QueryContext(Instant.parse("2000-01-01T00:00:00Z"), () -> false);
            assertThatThrownBy(() -> access.query(request, queryCtxDeadline))
                    .isInstanceOf(PlatformException.class)
                    .extracting(exception -> ((PlatformException) exception).code())
                    .isEqualTo(PlatformErrorCode.DEADLINE_EXCEEDED);
        }
    }

    @Test
    void checksCancellationDuringRowMapping() {
        AtomicBoolean cancelled = new AtomicBoolean();
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            QueryRequest<Long> request = new QueryRequest<>("mapping-cancellation",
                    "SELECT 1 AS value UNION ALL SELECT 2 AS value", Map.of(), row -> {
                long value = row.longValue(VALUE);
                cancelled.set(true);
                return value;
            });
            var queryContext = new QueryContext(ACTIVE.deadline(), cancelled::get);
            assertThatThrownBy(() -> access.query(request, queryContext))
                    .isInstanceOf(PlatformException.class)
                    .extracting(exception -> ((PlatformException) exception).code())
                    .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED);
        }
    }

    @Test
    void rejectsAnUnavailableDatabaseDuringTheStartupProbeWithoutDetails() {
        DataAccessConfiguration unavailable = new DataAccessConfiguration(
                "jdbc:postgresql://127.0.0.1:1/unavailable", "probe-user", "probe-secret");

        PostgresJdbiDataAccessFactory factory = new PostgresJdbiDataAccessFactory();
        assertThatThrownBy(() -> factory.open(unavailable))
                .isInstanceOf(PlatformException.class)
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining("probe-user")
                .hasMessageNotContaining("probe-secret")
                .satisfies(failure -> {
                    PlatformException exception = (PlatformException) failure;
                    assertThat(exception.diagnostics()).singleElement()
                            .extracting(Diagnostic::message)
                            .asString()
                            .isEqualTo("Database startup probe failed.");
                });
    }

    @Test
    void sanitizesSqlFailuresAndIncludesOnlyOperationName() {
        String secret = "driver-secret-value";
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            QueryRequest<Integer> request = new QueryRequest<>("broken-select", "SELECT " + secret,
                    Map.of(), row -> 1);

            assertThatThrownBy(() -> access.query(request, ACTIVE))
                    .isInstanceOf(PlatformException.class)
                    .hasMessageNotContaining(secret)
                    .satisfies(failure -> {
                        PlatformException exception = (PlatformException) failure;
                        assertThat(exception.code()).isEqualTo(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR);
                        assertThat(exception.diagnostics()).singleElement()
                                .extracting(Diagnostic::message)
                                .asString()
                                .contains("broken-select")
                                .doesNotContain(secret);
                    });
        }
    }

    @Test
    void mapsPostgresStatementTimeoutToDeadlineExceeded() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            QueryContext shortDeadline = new QueryContext(Instant.now().plusSeconds(1), () -> false);
            QueryRequest<Integer> request = new QueryRequest<>("sleeping-query",
                    "SELECT pg_sleep(5), 1", Map.of(), row -> 1);

            assertThatThrownBy(() -> access.query(request, shortDeadline))
                    .isInstanceOf(PlatformException.class)
                    .extracting(exception -> ((PlatformException) exception).code())
                    .isEqualTo(PlatformErrorCode.DEADLINE_EXCEEDED);
        }
    }

    @Test
    void closesAfterAQueryFailure() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            QueryRequest<Integer> request =
                    new QueryRequest<>("close-after-error", "SELECT invalid SQL", Map.of(), _ -> 1);
            assertThatThrownBy(() -> access.query(request, ACTIVE)).isInstanceOf(PlatformException.class);
        }
    }

    @Test
    void queryToDataSetMapsRowsToSchemaRecords() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            Schema schema = namedSchema();
            MaterializedDataSet dataSet = access.queryToDataSet(ACTIVE, "named-dataset",
                    "SELECT 'Ada' AS name, 7 AS amount", schema,
                    Map.of("name", "name", AMOUNT, AMOUNT));

            assertThat(dataSet.records()).singleElement().satisfies(schemaRecord -> {
                assertThat(schemaRecord.value(new FieldId("name"))).isEqualTo("Ada");
                assertThat(schemaRecord.value(new FieldId(AMOUNT))).isEqualTo(7L);
            });
        }
    }

    @Test
    void queryToDataSetBindsNamedParameters() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            Schema schema = namedSchema();
            MaterializedDataSet dataSet = access.queryToDataSet(ACTIVE, "param-dataset",
                    "SELECT :name AS name, :amount AS amount", Map.of("name", "Ada", AMOUNT, 7L), schema,
                    Map.of("name", "name", AMOUNT, AMOUNT));

            assertThat(dataSet.records()).singleElement().satisfies(schemaRecord -> {
                assertThat(schemaRecord.value(new FieldId("name"))).isEqualTo("Ada");
                assertThat(schemaRecord.value(new FieldId(AMOUNT))).isEqualTo(7L);
            });
        }
    }

    @Test
    void executePerformsWriteAndReturnsAffectedRows() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            access.execute(ACTIVE, "create-table", "CREATE TABLE IF NOT EXISTS demo_execute (name text)", Map.of());
            try {
                int affected = access.execute(ACTIVE, "insert-row",
                        "INSERT INTO demo_execute (name) VALUES (:name)", Map.of("name", "test"));

                assertThat(affected).isEqualTo(1);
            } finally {
                access.execute(ACTIVE, "drop-table", "DROP TABLE demo_execute", Map.of());
            }
        }
    }

    private static Schema namedSchema() {
        return new Schema("named", 1,
                List.of(new Field(new FieldId("name"), "name", new FieldType.StringType(), false, Optional.empty()),
                        new Field(new FieldId(AMOUNT), AMOUNT, new FieldType.Int64Type(), false, Optional.empty())),
                false);
    }

    @Test
    void pooledClientExecutesQueriesAndCloseReleasesThePool() {
        JdbiDataAccessFactory factory = new JdbiDataAccessFactory(
                List.of(new PostgresPlugin()), new JdbiPoolOptions(true, 2, 1, 5_000L, 1_800_000L));
        DataAccess access = factory.open(CONFIG);

        List<Row> rows = access.query(new QueryRequest<>("pooled-row", "SELECT :name AS name, :amount AS amount",
                        Map.of("name", "Ada", AMOUNT, 7L), row -> new Row(row.string("name"), row.longValue(AMOUNT))),
                ACTIVE);
        assertThat(rows).containsExactly(new Row("Ada", 7L));

        access.close();
        var req = simpleRequest("post-close");
        assertThatThrownBy(() -> access.query(req, ACTIVE))
                .isInstanceOf(PlatformException.class)
                .hasMessageNotContaining("jdbc:postgresql");
    }

    @Test
    void pooledClientServesConcurrentQueriesWithinPoolBounds() throws Exception {
        JdbiDataAccessFactory factory = new JdbiDataAccessFactory(
                List.of(new PostgresPlugin()), new JdbiPoolOptions(true, 2, 1, 5_000L, 1_800_000L));
        try (DataAccess access = factory.open(CONFIG)) {
            int threads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                List<Future<Long>> futures = new ArrayList<>();
                for (int index = 0; index < threads; index++) {
                    futures.add(executor.submit(
                            () -> access.query(simpleRequest("pooled-concurrent"), ACTIVE).getFirst()));
                }
                for (Future<Long> future : futures) {
                    assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(42L);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsAnUnavailableDatabaseDuringTheStartupProbeWithoutDetailsWhenPooled() {
        DataAccessConfiguration unavailable = new DataAccessConfiguration(
                "jdbc:postgresql://127.0.0.1:1/unavailable", "probe-user", "probe-secret");
        JdbiDataAccessFactory factory = new JdbiDataAccessFactory(
                List.of(new PostgresPlugin()), JdbiPoolOptions.defaults());

        assertThatThrownBy(() -> factory.open(unavailable))
                .isInstanceOf(PlatformException.class)
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining("probe-user")
                .hasMessageNotContaining("probe-secret")
                .satisfies(failure -> {
                    PlatformException exception = (PlatformException) failure;
                    assertThat(exception.diagnostics()).singleElement()
                            .extracting(Diagnostic::message)
                            .asString()
                            .isEqualTo("Database startup probe failed.");
                });
    }

    private static QueryRequest<Long> simpleRequest(String operation) {
        return new QueryRequest<>(operation, "SELECT 42 AS value", Map.of(), row -> row.longValue(VALUE));
    }

    @Test
    void streamQueryDeliversBatchesAndCountsRows() {
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            StreamingDataSet stream = access.streamQuery(ACTIVE, "streamed-orders",
                    "SELECT 'Ada' AS name, gs AS amount FROM generate_series(1, 5) gs",
                    namedSchema(), Map.of("name", "name", AMOUNT, AMOUNT),
                    new DataSetLimits(100, 100_000), 2);

            List<Integer> batchSizes = new ArrayList<>();
            long delivered = 0;
            Optional<List<SchemaRecord>> batch = stream.nextBatch();
            while (batch.isPresent()) {
                batchSizes.add(batch.get().size());
                delivered = stream.deliveredRowCount();
                batch = stream.nextBatch();
            }

            assertThat(batchSizes).containsExactly(2, 2, 1);
            assertThat(delivered).isEqualTo(5);
        }
    }

    @Test
    void streamQuerySanitizesFailuresWithOperationNameOnly() {
        String secret = "mid-stream-secret";
        try (DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG)) {
            // Whether the server rejects this at cursor creation or between
            // batch pulls, the caller sees only the sanitized diagnostic.
            assertThatThrownBy(() -> pullFirstBatch(access)).isInstanceOfSatisfying(PlatformException.class,
                    exception -> {
                        assertThat(exception.code()).isEqualTo(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR);
                        assertThat(exception.diagnostics()).singleElement()
                                .extracting(Diagnostic::message)
                                .asString()
                                .contains("broken-stream")
                                .doesNotContain(secret);
                    });
        }
    }

    private static void pullFirstBatch(DataAccess access) {
        try (StreamingDataSet stream = access.streamQuery(ACTIVE, "broken-stream",
                "SELECT 'Ada' AS name, CASE WHEN gs = 4 THEN 1 / 0 ELSE gs END AS amount "
                        + "FROM generate_series(1, 5) gs",
                namedSchema(), Map.of("name", "name", AMOUNT, AMOUNT),
                new DataSetLimits(100, 100_000), 2)) {
            stream.nextBatch();
        }
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Row(String name, long amount) {
    }
}
