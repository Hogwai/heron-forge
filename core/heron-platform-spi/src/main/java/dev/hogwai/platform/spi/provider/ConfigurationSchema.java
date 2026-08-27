package dev.hogwai.platform.spi.provider;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, JSON-schema-like description of a provider's raw configuration.
 *
 * <p>This is a small descriptive contract only: it declares the allowed field
 * names, the scalar kind of each field, the set of required field names and
 * optional deprecation messages. It never deserializes provider classes and
 * carries no values. All collections are defensively copied and exposed
 * immutably.
 *
 * @param allowedFields  the set of allowed field names
 * @param requiredFields the set of required field names; must be a subset of
 *                       {@code allowedFields}
 * @param fieldKinds     the scalar kind of each declared field; keys must be
 *                       in {@code allowedFields}
 * @param deprecations   the deprecation message of each deprecated field;
 *                       keys must be in {@code allowedFields}
 */
public record ConfigurationSchema(
        Set<String> allowedFields,
        Set<String> requiredFields,
        Map<String, ScalarKind> fieldKinds,
        Map<String, String> deprecations) {

    /**
     * Scalar kind of a configuration field.
     */
    public enum ScalarKind {
        /**
         * A UTF-8 string value.
         */
        STRING,
        /**
         * A boolean value.
         */
        BOOLEAN,
        /**
         * A 64-bit signed integer value.
         */
        INTEGER,
        /**
         * An arbitrary-precision decimal value.
         */
        NUMBER
    }

    /**
     * Creates a configuration schema.
     *
     * @param allowedFields  the set of allowed field names
     * @param requiredFields the set of required field names; must be a subset of
     *                       {@code allowedFields}
     * @param fieldKinds     the scalar kind of each declared field; keys must be
     *                       in {@code allowedFields}
     * @param deprecations   the deprecation message of each deprecated field;
     *                       keys must be in {@code allowedFields}
     * @throws NullPointerException     if any of the four arguments is
     *                                  {@code null}, if {@code allowedFields} or
     *                                  {@code requiredFields} contains a
     *                                  {@code null} name, or if
     *                                  {@code fieldKinds} or {@code deprecations}
     *                                  contains a {@code null} value
     * @throws IllegalArgumentException if a name is blank, if a required field is
     *                                  not allowed, if {@code fieldKinds} or
     *                                  {@code deprecations} contains a
     *                                  {@code null} key or references an unknown
     *                                  field, or if a deprecation message is blank
     */
    public ConfigurationSchema {
        Validator.validate(allowedFields, requiredFields, fieldKinds, deprecations);
        allowedFields = Set.copyOf(allowedFields);
        requiredFields = Set.copyOf(requiredFields);
        fieldKinds = Map.copyOf(fieldKinds);
        deprecations = Map.copyOf(deprecations);
    }

    /**
     * Private nested validator for {@link ConfigurationSchema}.
     *
     * <p>Kept as a private nested validator so that the public class stays
     * within the project's cyclomatic complexity budget.
     */
    private static final class Validator {

        private Validator() {
            // no instances
        }

        static void validate(Set<String> allowedFields, Set<String> requiredFields,
                             Map<String, ScalarKind> fieldKinds, Map<String, String> deprecations) {
            validateNames(allowedFields, "allowedFields");
            validateNames(requiredFields, "requiredFields");
            if (!allowedFields.containsAll(requiredFields)) {
                throw new IllegalArgumentException("requiredFields must be a subset of allowedFields");
            }
            validateKinds(allowedFields, fieldKinds);
            DeprecationValidator.validate(allowedFields, deprecations);
        }

        private static void validateNames(Set<String> names, String label) {
            for (String name : names) {
                Objects.requireNonNull(name, label + " must not contain null");
                if (name.isBlank()) {
                    throw new IllegalArgumentException(label + " must not contain blank names");
                }
            }
        }

        private static void validateKinds(Set<String> allowedFields, Map<String, ScalarKind> fieldKinds) {
            for (Map.Entry<String, ScalarKind> entry : fieldKinds.entrySet()) {
                if (!allowedFields.contains(entry.getKey())) {
                    throw new IllegalArgumentException("fieldKinds references unknown field: " + entry.getKey());
                }
            }
        }
    }

    /**
     * Private nested validator for the deprecation map of
     * {@link ConfigurationSchema}.
     *
     * <p>Kept as a private nested validator so that each class stays within the
     * project's cyclomatic complexity budget.
     */
    private static final class DeprecationValidator {

        private DeprecationValidator() {
            // no instances
        }

        static void validate(Set<String> allowedFields, Map<String, String> deprecations) {
            for (Map.Entry<String, String> entry : deprecations.entrySet()) {
                if (!allowedFields.contains(entry.getKey())) {
                    throw new IllegalArgumentException("deprecations references unknown field: " + entry.getKey());
                }
                if (entry.getValue().isBlank()) {
                    throw new IllegalArgumentException("deprecation message must not be blank");
                }
            }
        }
    }
}