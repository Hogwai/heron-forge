package dev.hogwai.platform.spi;

import java.util.Objects;

/**
 * Immutable diagnostic describing a single problem.
 *
 * <p>A diagnostic carries a stable {@link PlatformErrorCode}, a
 * {@link Severity}, an optional JSON Pointer-like YAML path (for example
 * {@code /spec/capabilities/1/inputs/orders}), a non-empty message and an
 * optional remediation hint. It is framework-independent and immutable.
 *
 * @param code        the stable error code
 * @param severity    the severity
 * @param path        the optional JSON Pointer-like path, or {@code null}
 * @param message     the non-empty message
 * @param remediation the optional remediation hint, or {@code null}
 */
public record Diagnostic(
        PlatformErrorCode code,
        Severity severity,
        String path,
        String message,
        String remediation) {

    /**
     * Compact constructor enforcing the diagnostic contract.
     *
     * @throws NullPointerException     if {@code code}, {@code severity} or
     *                                  {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank, or if
     *                                  {@code path}/{@code remediation} are
     *                                  present but blank
     */
    public Diagnostic {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (message == null) {
            throw new NullPointerException("message must not be null");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (path != null && path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (remediation != null && remediation.isBlank()) {
            throw new IllegalArgumentException("remediation must not be blank");
        }
    }

    /**
     * Creates a diagnostic without a path or remediation.
     *
     * @param code     the stable error code
     * @param severity the severity
     * @param message  the non-empty message
     * @return the diagnostic
     */
    public static Diagnostic of(PlatformErrorCode code, Severity severity, String message) {
        return new Diagnostic(code, severity, null, message, null);
    }

    /**
     * Creates a diagnostic with a path but no remediation.
     *
     * @param code     the stable error code
     * @param severity the severity
     * @param path     the optional JSON Pointer-like path, or {@code null}
     * @param message  the non-empty message
     * @return the diagnostic
     */
    public static Diagnostic of(PlatformErrorCode code, Severity severity, String path, String message) {
        return new Diagnostic(code, severity, path, message, null);
    }
}
