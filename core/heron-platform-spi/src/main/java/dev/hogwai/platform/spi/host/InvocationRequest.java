package dev.hogwai.platform.spi.host;

import java.time.Instant;
import java.util.Objects;

/** Request metadata supplied to a host application invocation.
 *
 * @param entrypointId       requested entrypoint identifier
 * @param requestId          request identifier
 * @param correlationId      correlation identifier
 * @param deadline           request deadline
 * @param cancellationSignal request cancellation signal
 */
public record InvocationRequest(
        String entrypointId,
        String requestId,
        String correlationId,
        Instant deadline,
        CancellationSignal cancellationSignal) {

    /** Validates the required request metadata. */
    public InvocationRequest {
        requireNonBlank(entrypointId, "entrypointId");
        requireNonBlank(requestId, "requestId");
        requireNonBlank(correlationId, "correlationId");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal must not be null");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
