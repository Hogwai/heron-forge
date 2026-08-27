package dev.hogwai.platform.spi.data.access;

import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Deadline and cancellation signal for one query.
 */
public record QueryContext(Instant deadline, BooleanSupplier cancellationSignal) {

    /**
     * Validates the query context values.
     */
    public QueryContext {
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal must not be null");
    }

    /**
     * Returns whether the cancellation signal reports a cancellation request.
     *
     * @return whether cancellation was requested
     */
    public boolean isCancellationRequested() {
        return cancellationSignal.getAsBoolean();
    }
}
