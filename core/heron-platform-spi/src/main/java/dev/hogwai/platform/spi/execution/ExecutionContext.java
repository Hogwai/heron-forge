package dev.hogwai.platform.spi.execution;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable context for a single capability execution.
 *
 * <p>It carries only the request identifier, the snapshot identifier, the
 * deadline, a {@link CancellationToken} and the correlation identifier. All
 * values are immutable and validated at construction. Framework-independent.
 */
public record ExecutionContext(String requestId, String snapshotId, Instant deadline,
                               CancellationToken cancellationToken, String correlationId) {

    /**
     * Creates an execution context.
     *
     * @param requestId         the non-blank request identifier
     * @param snapshotId        the non-blank snapshot identifier
     * @param deadline          the deadline
     * @param cancellationToken the cancellation token
     * @param correlationId     the non-blank correlation identifier
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if an identifier is blank
     */
    public ExecutionContext(String requestId, String snapshotId, Instant deadline,
                            CancellationToken cancellationToken, String correlationId) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        this.requestId = requestId;
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        if (snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be blank");
        }
        this.snapshotId = snapshotId;
        this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        this.correlationId = correlationId;
    }

    /**
     * Returns the request identifier.
     *
     * @return the request identifier
     */
    @Override
    public String requestId() {
        return requestId;
    }

    /**
     * Returns the snapshot identifier.
     *
     * @return the snapshot identifier
     */
    @Override
    public String snapshotId() {
        return snapshotId;
    }

    /**
     * Returns the deadline.
     *
     * @return the deadline
     */
    @Override
    public Instant deadline() {
        return deadline;
    }

    /**
     * Returns the cancellation token.
     *
     * @return the cancellation token
     */
    @Override
    public CancellationToken cancellationToken() {
        return cancellationToken;
    }

    /**
     * Returns the correlation identifier.
     *
     * @return the correlation identifier
     */
    @Override
    public String correlationId() {
        return correlationId;
    }
}
