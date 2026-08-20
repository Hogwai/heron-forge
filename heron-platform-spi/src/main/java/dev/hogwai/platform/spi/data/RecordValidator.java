package dev.hogwai.platform.spi.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Package-private validation helper for {@link SchemaRecord}.
 *
 * <p>Kept separate from {@link SchemaRecord} so that the public class stays a thin,
 * immutable holder while the validation logic remains within the data package.
 */
final class RecordValidator {

    private RecordValidator() {
    }

    /**
     * Validates the given values against the schema and returns an immutable
     * map of the accepted values.
     *
     * @param schema the schema the record conforms to
     * @param values the field values
     * @return an immutable map of the accepted values
     */
    static Map<FieldId, Object> validate(Schema schema, Map<FieldId, Object> values) {
        Map<FieldId, Object> copy = new LinkedHashMap<>();
        values.forEach((id, value) -> {
            Field field = schema.field(id).orElse(null);
            if (field == null) {
                if (!schema.openFields()) {
                    throw new IllegalArgumentException("unknown field: " + id);
                }
                if (value != null && !ScalarValues.isSupported(value)) {
                    throw new IllegalArgumentException("unsupported value for open field: " + id);
                }
            } else {
                validateValue(field, value, id);
            }
            copy.put(id, value);
        });
        validateRequired(schema, values);
        return Collections.unmodifiableMap(copy);
    }

    private static void validateRequired(Schema schema, Map<FieldId, Object> values) {
        if (schema.fields().stream().anyMatch(f -> f.isRequired() && !values.containsKey(f.id()))) {
            throw new IllegalArgumentException("missing required field");
        }
    }

    private static void validateValue(Field field, Object value, FieldId id) {
        if (value == null) {
            if (!field.nullable()) {
                throw new IllegalArgumentException("invalid value for field: " + id);
            }
        } else if (!field.type().accepts(value)) {
            throw new IllegalArgumentException("invalid value for field: " + id);
        }
    }
}
