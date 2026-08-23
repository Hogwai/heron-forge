package dev.hogwai.platform.spi.data;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of a single field within a {@link Schema}.
 *
 * <p>A field carries a {@link FieldId}, a non-blank display name, a
 * {@link FieldType}, a nullability flag and an optional default value. The
 * default value, when present, must be assignable to the field type.
 *
 * @param id           the field identifier
 * @param displayName  the non-blank display name
 * @param type         the field type
 * @param nullable     whether the field may hold a {@code null} value
 * @param defaultValue the optional default value
 */
public record Field(
        FieldId id,
        String displayName,
        FieldType type,
        boolean nullable,
        Optional<Object> defaultValue) {

    /**
     * Creates a field.
     *
     * @param id           the field identifier
     * @param displayName  the non-blank display name
     * @param type         the field type
     * @param nullable     whether the field may hold a {@code null} value
     * @param defaultValue the optional default value
     * @throws NullPointerException     if {@code id}, {@code displayName},
     *                                  {@code type} or {@code defaultValue} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank, or if
     *                                  the default value is not assignable to
     *                                  the field type
     */
    public Field {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        defaultValue.ifPresent(value -> {
            if (!type.accepts(value)) {
                throw new IllegalArgumentException("default value is not assignable to field type: " + id);
            }
        });
    }

    /**
     * A field is required when it is non-nullable and has no default value.
     *
     * @return {@code true} if the field is required
     */
    public boolean isRequired() {
        return !nullable && defaultValue.isEmpty();
    }

    /**
     * Returns a string representation of the field without leaking the default
     * value.
     *
     * @return a string representation of the field
     */
    @Override
    public String toString() {
        return "Field[id=" + id + ", displayName=" + displayName + ", type=" + type
                + ", nullable=" + nullable + ", defaultValue="
                + (defaultValue.isPresent() ? "present" : "empty") + "]";
    }
}