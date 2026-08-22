package dev.hogwai.platform.spi.host;

import java.util.Objects;

/** Successful invocation result containing a structured payload.
 *
 * @param payload structured invocation result
 */
public record InvocationSuccess(StructuredPayload payload) implements InvocationResult {

    /** Requires a structured result. */
    public InvocationSuccess {
        Objects.requireNonNull(payload, "payload must not be null");
    }
}
