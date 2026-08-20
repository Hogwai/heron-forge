package dev.hogwai.platform.spi;

/**
 * Severity of a {@link Diagnostic}.
 *
 * <p>Framework-independent and immutable.
 */
public enum Severity {
    /** Informational diagnostic. */
    INFO,
    /** Warning diagnostic. */
    WARNING,
    /** Error diagnostic. */
    ERROR
}
