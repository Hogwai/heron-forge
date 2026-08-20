package dev.hogwai.platform.data.jdbi;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

/** Jdbi implementation of the framework-independent data access contract. */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class JdbiDataAccess implements DataAccess {

    private final Jdbi jdbi;

    /**
     * Creates a data access client from a thread-safe Jdbi instance.
     *
     * @param jdbi the Jdbi client
     */
    JdbiDataAccess(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
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
     * @throws PlatformException if the query fails, is cancelled, or exceeds its deadline
     */
    @Override
    public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        try {
            checkContext(context);
            int queryTimeoutSeconds = queryTimeoutSeconds(context.deadline());
            return jdbi.withHandle(handle -> {
                Query query = handle.createQuery(request.sql()).bindMap(request.parameters());
                query.setQueryTimeout(queryTimeoutSeconds);
                List<T> rows = query.map((resultSet, statementContext) -> {
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

    /** Jdbi owns no long-lived handle here, so closing the client is intentionally a no-op. */
    @Override
    public void close() {
        // Each handle is created and closed by Jdbi's withHandle call.
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
                "Data operation '" + operation + "' failed.");
    }

    private static PlatformException cancellationFailure(String operation) {
        return failure(PlatformErrorCode.CANCELLATION_REQUESTED,
                "Data operation '" + operation + "' was cancelled.");
    }

    private static PlatformException deadlineFailure(String operation) {
        return failure(PlatformErrorCode.DEADLINE_EXCEEDED,
                "Data operation '" + operation + "' exceeded its deadline.");
    }

    private static PlatformException failure(PlatformErrorCode code, String message) {
        Diagnostic diagnostic = new Diagnostic(code, Severity.ERROR, null, message, null);
        return new PlatformException(code, List.of(diagnostic));
    }
}
