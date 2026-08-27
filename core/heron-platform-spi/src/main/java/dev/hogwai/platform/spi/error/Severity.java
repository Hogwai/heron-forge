package dev.hogwai.platform.spi.error;

import dev.hogwai.platform.spi.Diagnostic;

/**
 * Severity of a {@link Diagnostic}.
 *
 */
public enum Severity {
    /**
     * Informational diagnostic.
     */
    INFO,
    /**
     * Warning diagnostic.
     */
    WARNING,
    /**
     * Error diagnostic.
     */
    ERROR
}
