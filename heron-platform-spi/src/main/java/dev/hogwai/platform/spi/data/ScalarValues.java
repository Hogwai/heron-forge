package dev.hogwai.platform.spi.data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Package-private helper describing the immutable scalar values supported by
 * the v1 data model.
 *
 * <p>Open fields may only hold {@code null} or one of these immutable scalars;
 * nested structures and arbitrary Java objects are not permitted.
 */
final class ScalarValues {

    private ScalarValues() {
    }

    /**
     * Returns whether the given value is a supported immutable scalar.
     *
     * @param value the value to test, or {@code null}
     * @return {@code true} if the value is a supported scalar
     */
    static boolean isSupported(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Long
                || value instanceof BigDecimal
                || value instanceof Instant;
    }
}
