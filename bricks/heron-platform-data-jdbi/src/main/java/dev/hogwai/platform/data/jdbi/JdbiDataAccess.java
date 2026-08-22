package dev.hogwai.platform.data.jdbi;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataRow;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

import com.zaxxer.hikari.HikariDataSource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Jdbi implementation of the framework-independent data access contract. */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class JdbiDataAccess implements DataAccess {

    private static final DataSetLimits DEFAULT_LIMITS = new DataSetLimits(1_000, 1_000_000);
    public static final String CONTEXT_MUST_NOT_BE_NULL = "context must not be null";
    public static final String REQUEST_MUST_NOT_BE_NULL = "request must not be null";

    private final Jdbi jdbi;
    private final HikariDataSource pooledDataSource;

    /**
     * Creates a data access client from a thread-safe Jdbi instance.
     *
     * @param jdbi the Jdbi client
     */
    JdbiDataAccess(Jdbi jdbi) {
        this(jdbi, null);
    }

    /**
     * Creates a pooled data access client.
     *
     * @param jdbi             the Jdbi client backed by the pool
     * @param pooledDataSource the pool closed together with this client
     */
    JdbiDataAccess(Jdbi jdbi, HikariDataSource pooledDataSource) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
        this.pooledDataSource = pooledDataSource;
    }

    /**
     * Executes one query using a handle owned only for the duration of this call.
     *
     * <p>The JDBC query timeout is applied only to this query. Cancellation is
     * checked before execution, while mapping, and after materialization, but a
     * cancellation signal cannot actively interrupt a server call already
     * blocked in the database server. The deadline timeout is the available server-side
     * interruption mechanism; no statement-cancelling watcher is installed.
     *
     * @param request the query request
     * @param context the cancellation and deadline context
     * @param <T> the mapped row type
     * @return all mapped rows
     * @throws PlatformException if the query fails, is canceled, or exceeds its deadline
     */
    @Override
    public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
        Objects.requireNonNull(request, REQUEST_MUST_NOT_BE_NULL);
        Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
        try {
            checkContext(context);
            int queryTimeoutSeconds = queryTimeoutSeconds(context.deadline());
            return jdbi.withHandle(handle -> {
                Query query = handle.createQuery(request.sql()).bindMap(request.parameters());
                query.setQueryTimeout(queryTimeoutSeconds);
                List<T> rows = query.map((resultSet, _) -> {
                    checkContext(context);
                    return request.mapper().map(new JdbiDataRow(resultSet));
                }).list();
                checkContext(context);
                return rows;
            });
        } catch (PlatformException failure) {
            if (failure.code() == PlatformErrorCode.CANCELLATION_REQUESTED) {
                throw cancellationFailure(request.operation());
            }
            if (failure.code() == PlatformErrorCode.DEADLINE_EXCEEDED) {
                throw deadlineFailure(request.operation());
            }
            throw queryFailure(request.operation());
        } catch (RuntimeException _) {
            if (context.isCancellationRequested()) {
                throw cancellationFailure(request.operation());
            }
            if (!Instant.now().isBefore(context.deadline())) {
                throw deadlineFailure(request.operation());
            }
            throw queryFailure(request.operation());
        }
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Schema schema, Map<String, String> columnByField) {
        return queryToDataSet(context, operation, sql, Map.of(), schema, columnByField, DEFAULT_LIMITS);
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Schema schema, Map<String, String> columnByField, DataSetLimits limits) {
        return queryToDataSet(context, operation, sql, Map.of(), schema, columnByField, limits);
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Map<String, ?> parameters, Schema schema, Map<String, String> columnByField) {
        return queryToDataSet(context, operation, sql, parameters, schema, columnByField, DEFAULT_LIMITS);
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Map<String, ?> parameters, Schema schema, Map<String, String> columnByField, DataSetLimits limits) {
        Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(columnByField, "columnByField must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
        QueryRequest<SchemaRecord> request = new QueryRequest<>(operation, sql, parameters,
                row -> mapRow(row, schema, columnByField));
        List<SchemaRecord> records = query(request, context);
        long estimate = Math.multiplyExact(records.size(), 256L);
        return new MaterializedDataSet(schema, records,
                new DataSetMetadata(operation, limits), estimate);
    }

    @Override
    public int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters) {
        Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
        Objects.requireNonNull(parameters, "parameters must not be null");
        try {
            checkContext(context);
            return jdbi.withHandle(handle -> handle.createUpdate(sql).bindMap(parameters).execute());
        } catch (PlatformException failure) {
            if (failure.code() == PlatformErrorCode.CANCELLATION_REQUESTED) {
                throw cancellationFailure(operation);
            }
            if (failure.code() == PlatformErrorCode.DEADLINE_EXCEEDED) {
                throw deadlineFailure(operation);
            }
            throw queryFailure(operation);
        } catch (RuntimeException _) {
            if (context.isCancellationRequested()) {
                throw cancellationFailure(operation);
            }
            if (!Instant.now().isBefore(context.deadline())) {
                throw deadlineFailure(operation);
            }
            throw queryFailure(operation);
        }
    }

    private static SchemaRecord mapRow(DataRow row, Schema schema, Map<String, String> columnByField) {
        Map<FieldId, Object> values = new HashMap<>();
        for (Field field : schema.fields()) {
            String column = columnByField.get(field.id().value());
            if (column == null) {
                throw new IllegalArgumentException("No column mapped for field %s".formatted(field.id().value()));
            }
            values.put(field.id(), readValue(row, column, field.type()));
        }
        return SchemaRecord.of(schema, values);
    }

    private static Object readValue(DataRow row, String column, FieldType type) {
        if (type instanceof FieldType.StringType) {
            return row.string(column);
        }
        if (type instanceof FieldType.Int64Type) {
            return row.longValue(column);
        }
        if (type instanceof FieldType.InstantType) {
            return row.instant(column);
        }
        throw new IllegalArgumentException("Unsupported field type for column %s: %s".formatted(type, column));
    }

    /**
     * Closes the backing pool when the client is pooled. Unpooled clients own
     * no long-lived connection: each handle is created and closed by Jdbi's
     * withHandle call.
     */
    @Override
    public void close() {
        if (pooledDataSource != null) {
            pooledDataSource.close();
        }
    }

    private static void checkContext(QueryContext context) {
        if (context.isCancellationRequested()) {
            throw new PlatformException(PlatformErrorCode.CANCELLATION_REQUESTED, List.of());
        }
        if (!Instant.now().isBefore(context.deadline())) {
            throw new PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, List.of());
        }
    }

    private static int queryTimeoutSeconds(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, List.of());
        }
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        if (seconds < 1) {
            return 1;
        }
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private static PlatformException queryFailure(String operation) {
        return failure(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR,
                "Data operation '%s' failed.".formatted(operation));
    }

    private static PlatformException cancellationFailure(String operation) {
        return failure(PlatformErrorCode.CANCELLATION_REQUESTED,
                "Data operation '%s' was cancelled.".formatted(operation));
    }

    private static PlatformException deadlineFailure(String operation) {
        return failure(PlatformErrorCode.DEADLINE_EXCEEDED,
                "Data operation '%s' exceeded its deadline.".formatted(operation));
    }

    private static PlatformException failure(PlatformErrorCode code, String message) {
        Diagnostic diagnostic = new Diagnostic(code, Severity.ERROR, null, message, null);
        return new PlatformException(code, List.of(diagnostic));
    }
}
