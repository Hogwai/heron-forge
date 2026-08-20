package dev.hogwai.platform.spi.data;

import java.util.Objects;

/**
 * Immutable identifier of a {@link Field}.
 *
 * <p>The identifier must be non-null, non-blank and free of whitespace.
 * Framework-independent and immutable.
 *
 * @param value the field identifier
 */
public record FieldId(String value) {

    /**
     * Compact constructor enforcing the field identifier contract.
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank or contains
     *                                  whitespace
     */
    public FieldId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (containsWhitespace(value)) {
            throw new IllegalArgumentException("value must not contain whitespace");
        }
    }

    private static boolean containsWhitespace(String value) {
        return value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    @Override
    public String toString() {
        return value;
    }
}
