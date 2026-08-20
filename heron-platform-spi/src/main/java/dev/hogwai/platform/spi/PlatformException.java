package dev.hogwai.platform.spi;

import java.util.List;
import java.util.Objects;

/**
 * Framework-independent runtime exception carrying a stable
 * {@link PlatformErrorCode} and an immutable list of {@link Diagnostic}s.
 *
 * <p>The message is stable and derived only from the error code and the number
 * of diagnostics; it never incorporates the content of the diagnostics. The
 * {@link #diagnostics()} accessor exposes the diagnostics provided by the
 * caller as-is: their content is not filtered by this exception.
 */
public final class PlatformException extends RuntimeException {

    /** The stable error code. */
    private final PlatformErrorCode code;

    /**
     * The diagnostics are intentionally kept non-transient and immutable.
     *
     * <p>Making this field {@code transient} would silently drop the diagnostics
     * during Java serialization and yield a deserialized exception whose state
     * is inconsistent with the one that was serialized. {@link Diagnostic} and
     * the list are deliberately not made {@link java.io.Serializable}: no Java
     * serialization contract is part of this SPI, so the field is exempt from
     * S1948.
     */
    @SuppressWarnings("java:S1948") // Non-transient by design; no Java serialization contract is exposed.
    private final List<Diagnostic> diagnostics;

    /**
     * Creates an exception with the given code and diagnostics.
     *
     * @param code        the stable error code
     * @param diagnostics the diagnostics; copied defensively and exposed immutably
     */
    public PlatformException(PlatformErrorCode code, List<Diagnostic> diagnostics) {
        this(code, diagnostics, null);
    }

    /**
     * Creates an exception with the given code, diagnostics and cause.
     *
     * @param code        the stable error code
     * @param diagnostics the diagnostics; copied defensively and exposed immutably
     * @param cause       the optional cause, or {@code null}
     */
    public PlatformException(PlatformErrorCode code, List<Diagnostic> diagnostics, Throwable cause) {
        super(buildMessage(code, diagnostics), cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Returns error code of exception
     * @return the stable error code
     */
    public PlatformErrorCode code() {
        return code;
    }

    /**
     * Returns diagnostics of exception
     * @return an immutable view of the diagnostics
     */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static String buildMessage(PlatformErrorCode code, List<Diagnostic> diagnostics) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        return "Platform error " + code + " with " + diagnostics.size() + " diagnostic(s)";
    }
}
