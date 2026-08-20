package dev.hogwai.platform.spi;

import java.util.Objects;

/**
 * Immutable identifier of a data port.
 *
 * <p>The identifier must be non-null, non-blank and free of whitespace. It is
 * framework-independent and immutable.
 *
 * @param value the port identifier
 */
public record PortId(String value) {

    /**
     * Compact constructor enforcing the port identifier contract.
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank or contains whitespace
     */
    public PortId {
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
