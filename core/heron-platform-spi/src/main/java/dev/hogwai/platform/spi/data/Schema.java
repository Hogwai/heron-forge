package dev.hogwai.platform.spi.data;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of a data schema.
 *
 * <p>A schema carries a non-blank identifier, a strictly positive version, an
 * ordered immutable list of {@link Field}s and an {@code openFields} flag.
 * Duplicate field identifiers or display names are rejected.
 * Framework-independent and immutable.
 */
@SuppressWarnings("java:S6206")
public record Schema(String identifier,
                     int version,
                     List<Field> fields,
                     boolean openFields) {

    /**
     * Creates a schema.
     *
     * @param identifier the non-blank schema identifier
     * @param version    the strictly positive schema version
     * @param fields     the ordered, immutable list of fields
     * @param openFields whether unknown fields are permitted in records
     * @throws NullPointerException     if {@code identifier} or {@code fields}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code identifier} is blank, if
     *                                  {@code version} is not strictly positive,
     *                                  or if a field id or display name is
     *                                  duplicated
     */
    public Schema(String identifier, int version, List<Field> fields, boolean openFields) {
        Objects.requireNonNull(identifier, "identifier must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be strictly positive");
        }
        Objects.requireNonNull(fields, "fields must not be null");
        SchemaValidator.validateFields(fields);
        this.identifier = identifier;
        this.version = version;
        this.fields = List.copyOf(fields);
        this.openFields = openFields;
    }

    /**
     * Returns the non-blank schema identifier.
     *
     * @return the non-blank schema identifier
     */
    @Override
    public String identifier() {
        return identifier;
    }

    /**
     * Returns the strictly positive schema version.
     *
     * @return the strictly positive schema version
     */
    @Override
    public int version() {
        return version;
    }

    /**
     * Returns an immutable view of the ordered fields.
     *
     * @return an immutable view of the ordered fields
     */
    @Override
    public List<Field> fields() {
        return fields;
    }

    /**
     * Returns whether unknown fields are permitted in records.
     *
     * @return whether unknown fields are permitted in records
     */
    @Override
    public boolean openFields() {
        return openFields;
    }

    /**
     * Returns the field with the given identifier, if present.
     *
     * @param id the field identifier
     * @return the matching field, or {@link Optional#empty()} if absent
     */
    public Optional<Field> field(FieldId id) {
        Objects.requireNonNull(id, "id must not be null");
        for (Field field : fields) {
            if (field.id().equals(id)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
