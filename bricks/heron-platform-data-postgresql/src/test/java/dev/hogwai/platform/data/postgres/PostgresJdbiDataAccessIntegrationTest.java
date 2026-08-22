package dev.hogwai.platform.data.postgres;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in PostgreSQL coverage for the PostgreSQL Jdbi data access implementation. */
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
            QueryRequest<Integer> request = new QueryRequest<>("guarded-query", "SELECT 1", Map.of(),
                    row -> 1);
            assertThatThrownBy(() -> access.query(request, new QueryContext(ACTIVE.deadline(), () -> true)))
                    .isInstanceOf(PlatformException.class)
                    .extracting(exception -> ((PlatformException) exception).code())
                    .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED);
            assertThatThrownBy(() -> access.query(request,
                    new QueryContext(Instant.parse("2000-01-01T00:00:00Z"), () -> false)))
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

            assertThatThrownBy(() -> access.query(request, new QueryContext(ACTIVE.deadline(), cancelled::get)))
                    .isInstanceOf(PlatformException.class)
                    .extracting(exception -> ((PlatformException) exception).code())
                    .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED);
        }
    }

    @Test
    void rejectsAnUnavailableDatabaseDuringTheStartupProbeWithoutDetails() {
        DataAccessConfiguration unavailable = new DataAccessConfiguration(
                "jdbc:postgresql://127.0.0.1:1/unavailable", "probe-user", "probe-secret");

        assertThatThrownBy(() -> new PostgresJdbiDataAccessFactory().open(unavailable))
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
        DataAccess access = new PostgresJdbiDataAccessFactory().open(CONFIG);
        try {
            QueryRequest<Integer> request = new QueryRequest<>("close-after-error", "SELECT invalid SQL",
                    Map.of(), row -> 1);
            assertThatThrownBy(() -> access.query(request, ACTIVE)).isInstanceOf(PlatformException.class);
        } finally {
            access.close();
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

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Row(String name, long amount) {
    }
}
