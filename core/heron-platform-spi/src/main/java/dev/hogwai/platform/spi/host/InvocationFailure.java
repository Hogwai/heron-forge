package dev.hogwai.platform.spi.host;

import java.util.Objects;

/** Failed invocation result containing a stable code and safe message.
 *
 * @param code    stable failure category
 * @param message safe failure message
 */
public record InvocationFailure(FailureCode code, String message) implements InvocationResult {

    /** Requires a failure code and message. */
    public InvocationFailure {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
