package dev.hogwai.platform.spi.data;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable record of field values bound to a {@link Schema}.
 *
 * <p>A record holds an immutable map from {@link FieldId} to value. It
 * distinguishes an absent key from a key present with a {@code null} value.
 * Unknown fields are rejected when the schema is closed ({@code openFields}
 * is {@code false}), and missing required fields are rejected.
 * Framework-independent and immutable.
 */
public final class SchemaRecord {

    private final Schema schema;
    private final Map<FieldId, Object> values;

    private SchemaRecord(Schema schema, Map<FieldId, Object> values) {
        this.schema = schema;
        this.values = values;
    }

    /**
     * Creates a record bound to the given schema.
     *
     * @param schema the schema the record conforms to
     * @param values the field values
     * @return the record
     * @throws NullPointerException     if {@code schema}, {@code values} or any
     *                                  key is {@code null}
     * @throws IllegalArgumentException if a required field is missing, if a
     *                                  non-nullable field holds {@code null}, if
     *                                  a value is not assignable to its field
     *                                  type, if an unknown field holds a value
     *                                  that is not a supported immutable scalar,
     *                                  or if an unknown field is present while
     *                                  the schema is closed
     */
    public static SchemaRecord of(Schema schema, Map<FieldId, Object> values) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(values, "values must not be null");
        return new SchemaRecord(schema, RecordValidator.validate(schema, values));
    }

    /**
     * Returns the schema the record conforms to.
     *
     * @return the schema the record conforms to
     */
    public Schema schema() {
        return schema;
    }

    /**
     * Returns whether the given field key is present in this record.
     *
     * @param id the field identifier
     * @return {@code true} if the key is present (even with a {@code null} value)
     */
    public boolean contains(FieldId id) {
        return values.containsKey(id);
    }

    /**
     * Returns the raw value for the given field, or {@code null} if absent.
     *
     * @param id the field identifier
     * @return the raw value, or {@code null} if absent
     */
    public Object get(FieldId id) {
        return values.get(id);
    }

    /**
     * Returns the effective value for the given field, applying the field's
     * default when the key is absent.
     *
     * @param id the field identifier
     * @return the effective value, or {@code null} if absent and no default
     */
    public Object value(FieldId id) {
        if (values.containsKey(id)) {
            return values.get(id);
        }
        return schema.field(id).flatMap(Field::defaultValue).orElse(null);
    }

    /**
     * Returns an immutable view of the raw field values.
     *
     * @return the immutable field values
     */
    public Map<FieldId, Object> values() {
        return values;
    }
}
