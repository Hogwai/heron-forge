package dev.hogwai.platform.spi.invocation;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Request metadata for an asynchronous worker invocation.
 *
 * @param endpoint the endpoint identifier to invoke
 * @param payload  the request payload (key-value)
 * @param timeout  the maximum wait duration
 * @param metadata additional metadata (headers, etc.)
 */
public record WorkerInvocationRequest(
        String endpoint,
        Map<String, Object> payload,
        Duration timeout,
        Map<String, String> metadata
) {

    /**
     * Validates the required fields.
     */
    public WorkerInvocationRequest {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Objects.requireNonNull(metadata, "metadata must not be null");
    }
}
