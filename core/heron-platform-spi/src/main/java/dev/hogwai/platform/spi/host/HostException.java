package dev.hogwai.platform.spi.host;

import java.io.Serial;
import java.util.Objects;

/**
 * Checked host lifecycle error with a safe public message.
 */
public final class HostException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;
    private final transient Throwable localCause;

    /**
     * Creates a host exception without a local cause.
     *
     * @param safeMessage message safe to expose to callers
     */
    public HostException(String safeMessage) {
        this(safeMessage, null);
    }

    /**
     * Creates a host exception retaining the cause only for local diagnostics.
     *
     * @param safeMessage message safe to expose to callers
     * @param cause       local cause for diagnostics
     */
    public HostException(String safeMessage, Throwable cause) {
        super(requireSafeMessage(safeMessage));
        localCause = cause;
    }

    /**
     * Returns the retained local cause, which is not serialized by this module.
     */
    @Override
    public synchronized Throwable getCause() {
        return localCause;
    }

    private static String requireSafeMessage(String message) {
        Objects.requireNonNull(message, "safeMessage must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("safeMessage must not be blank");
        }
        return message;
    }
}
