package dev.hogwai.platform.runtime.config.safe;

import java.math.BigDecimal;

/**
 * Recognizes the canonical v1 scalar value types.
 *
 * <p>Package-private helper that keeps {@link SafeConfig} within the project's
 * cyclomatic complexity budget.
 */
final class SafeScalars {

    private SafeScalars() {
        // no instances
    }

    static boolean isScalar(Object value) {
        return value == null || value instanceof String || value instanceof Boolean
                || value instanceof Long || value instanceof BigDecimal;
    }
}
