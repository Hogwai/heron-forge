package dev.hogwai.platform.data.jdbi;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataRow;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.ResultIterator;
import org.jdbi.v3.core.statement.Query;

import com.zaxxer.hikari.HikariDataSource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Jdbi implementation of the data access contract.
 */
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
     * @param <T>     the mapped row type
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
            Map<String, ?> parameters, Schema schema, Map<String, String> columnByField,
            DataSetLimits limits) {
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

    /**
     * {@inheritDoc}
     *
     * <p>Resource ownership is deliberately transferred to the returned
     * dataset: the handle and the cursor are released by
     * {@code ReleasingIterator} when the caller closes the stream (or eagerly
     * on a creation failure below). Deferred close is the whole point of
     * streaming, hence the S2095 suppressions.
     */
    @Override
    @SuppressWarnings("java:S2095")
    public StreamingDataSet streamQuery(QueryContext context, String operation, String sql,
                                        Schema schema, Map<String, String> columnByField,
                                        DataSetLimits limits,
                                        int batchSize) {
        Objects.requireNonNull(context, CONTEXT_MUST_NOT_BE_NULL);
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(columnByField, "columnByField must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
        checkContext(context);

        int queryTimeoutSeconds = queryTimeoutSeconds(context.deadline());
        Handle handle = jdbi.open();
        ResultIterator<SchemaRecord> cursor;
        try {
            // pgjdbc honors the fetch size only outside autocommit; cursor mode
            // is what keeps large results off the wire buffer.
            handle.getConnection().setAutoCommit(false);
            cursor = getRecordResultIterator(sql, schema, columnByField, batchSize, handle,
                    queryTimeoutSeconds);
        } catch (Exception original) {
            // Roll back quietly: with autocommit off Jdbi would otherwise raise
            // its own TransactionException on close and mask the real failure.
            releaseQuietly(handle);
            if (original instanceof PlatformException platformFailure) {
                throw platformFailure;
            }
            if (context.isCancellationRequested()) {
                throw cancellationFailure(operation);
            }
            if (!Instant.now().isBefore(context.deadline())) {
                throw deadlineFailure(operation);
            }
            throw queryFailure(operation);
        }
        return new SanitizedStream(operation,
                StreamingDataSet.over(schema, new ReleasingIterator(cursor, handle),
                        limits, batchSize, context.deadline(),
                        context::isCancellationRequested));
    }

    /** Rolls back then closes quietly: release failures never mask the original. */
    private static void releaseQuietly(Handle handle) {
        try {
            handle.rollback();
        } catch (RuntimeException _) {
            // best effort
        }
        try {
            handle.close();
        } catch (RuntimeException _) {
            // best effort
        }
    }

    /**
     * Builds the lazy cursor.
     * The {@link Query} is closed together with the returned iterator (Jdbi closes the statement when its result iterator
     * closes), which the streaming dataset owns.
     */
    @SuppressWarnings("java:S2095")
    private static ResultIterator<SchemaRecord> getRecordResultIterator(String sql,
                                                                        Schema schema,
                                                                        Map<String, String> columnByField,
                                                                        int batchSize, Handle handle,
                                                                        int queryTimeoutSeconds) {
        Query query = handle.createQuery(sql);
        query.setQueryTimeout(queryTimeoutSeconds);
        // Enables JDBC cursor mode: without a fetch size the driver buffers the whole result on every pull.
        query.setFetchSize(batchSize);
        return query.map((resultSet, _) ->
                        mapRow(new JdbiDataRow(resultSet), schema, columnByField))
                .iterator();
    }

    /**
     * Iterator whose close releases both the Jdbi cursor and the owning
     * handle; the streaming data set closes it through its AutoCloseable hook.
     */
    private record ReleasingIterator(ResultIterator<SchemaRecord> cursor, Handle handle)
            implements Iterator<SchemaRecord>, AutoCloseable {

        @Override
        public boolean hasNext() {
            return cursor.hasNext();
        }

        @Override
        public SchemaRecord next() {
            return cursor.next();
        }

        /**
         * Cursor mode runs inside a transaction (autocommit off).
         * Reads are rolled back before release so Jdbi's forceEndTransaction check never fires on close.
         */
        @Override
        public void close() {
            try {
                cursor.close();
            } finally {
                try {
                    handle.rollback();
                } catch (RuntimeException _) {
                    // the handle close below still releases.
                }
                handle.close();
            }
        }
    }

    /**
     * Decorator mapping mid-stream failures to sanitized diagnostics.
     */
    private record SanitizedStream(String operation, StreamingDataSet delegate) implements StreamingDataSet {

        @Override
        public Schema schema() {
            return delegate.schema();
        }

        @Override
        public Optional<List<SchemaRecord>> nextBatch() {
            try {
                return delegate.nextBatch();
            } catch (IllegalStateException contractMisuse) {
                throw contractMisuse;
            } catch (RuntimeException failure) {
                releaseQuietly();
                if (failure instanceof PlatformException platformFailure) {
                    throw platformFailure;
                }
                throw queryFailure(operation);
            }
        }

        @Override
        public long deliveredRowCount() {
            return delegate.deliveredRowCount();
        }

        @Override
        public void close() {
            delegate.close();
        }

        private void releaseQuietly() {
            try {
                delegate.close();
            } catch (RuntimeException _) {
                // The original failure takes precedence over release errors.
            }
        }
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
