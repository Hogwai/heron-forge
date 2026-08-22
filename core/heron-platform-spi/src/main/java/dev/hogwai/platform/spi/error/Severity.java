package dev.hogwai.platform.spi.error;

import dev.hogwai.platform.spi.Diagnostic;

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
