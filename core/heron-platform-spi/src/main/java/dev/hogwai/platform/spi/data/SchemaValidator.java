package dev.hogwai.platform.spi.data;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Package-private validation helper for {@link Schema}.
 *
 * <p>Kept separate from {@link Schema} so that the public class stays a thin,
 * immutable holder while the validation logic remains within the data package.
 */
final class SchemaValidator {

    private SchemaValidator() {
    }

    /**
     * Validates that the given fields contain no duplicate identifiers or
     * display names.
     *
     * @param fields the fields to validate
     */
    static void validateFields(List<Field> fields) {
        Set<FieldId> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Field field : fields) {
            Objects.requireNonNull(field, "field must not be null");
            if (!ids.add(field.id())) {
                throw new IllegalArgumentException("duplicate field id: " + field.id());
            }
            if (!names.add(field.displayName())) {
                throw new IllegalArgumentException("duplicate field name: " + field.displayName());
            }
        }
    }
}
