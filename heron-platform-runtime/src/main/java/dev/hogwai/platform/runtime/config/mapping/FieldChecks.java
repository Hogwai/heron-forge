package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared field-level checks used by the config mappers.
 *
 * <p>Package-private helper that keeps the public
 * {@link dev.hogwai.platform.runtime.config.ConfigValidator} within the
 * project's cyclomatic complexity budget.
 */
final class FieldChecks {

    private static final Pattern VERSION =
            Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    private FieldChecks() {
        // no instances
    }

    /**
     * Returns whether the version is canonical {@code major.minor.patch}.
     *
     * @param version the version to inspect
     * @return {@code true} if canonical
     */
    static boolean isCanonicalVersion(String version) {
        return VERSION.matcher(version).matches();
    }

    /**
     * Parses a capability type string into a {@link CapabilityKind}.
     *
     * @param type        the type string
     * @param path        the structural path
     * @param diagnostics the diagnostics collector
     * @return the kind, or {@code null} if unsupported
     */
    static CapabilityKind parseKind(String type, String path, List<Diagnostic> diagnostics) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case "source":
                return CapabilityKind.SOURCE;
            case "transform":
                return CapabilityKind.TRANSFORM;
            default:
                diagnostics.add(Diagnostics.schemaError(path,
                        "unsupported capability type", "use 'source' or 'transform'"));
                return null;
        }
    }

    /**
     * Reads a required string field from a mapping node.
     *
     * @param parent      the mapping node
     * @param field       the known field name
     * @param path        the structural parent path
     * @param diagnostics the diagnostics collector
     * @return the value, or {@code null} if missing or invalid
     */
    static String requiredString(JsonNode parent, String field, String path, List<Diagnostic> diagnostics) {
        JsonNode node = parent.get(field);
        String fieldPath = Diagnostics.childPath(path, field);
        if (node == null) {
            diagnostics.add(Diagnostics.schemaError(fieldPath,
                    "missing required member '" + field + "'", "provide a value for '" + field + "'"));
            return null;
        }
        if (!node.isTextual()) {
            diagnostics.add(Diagnostics.schemaError(fieldPath,
                    "member '" + field + "' must be a string", "provide a string value"));
            return null;
        }
        String value = node.textValue();
        if (value.isBlank()) {
            diagnostics.add(Diagnostics.schemaError(fieldPath,
                    "member '" + field + "' must not be blank", "provide a non-blank value"));
            return null;
        }
        return value;
    }

    /**
     * Reports unknown fields on a mapping node using a safe structural path.
     *
     * @param object      the mapping node
     * @param allowed     the set of allowed field names
     * @param path        the structural parent path
     * @param diagnostics the diagnostics collector
     */
    static void rejectUnknownFields(JsonNode object, Set<String> allowed, String path,
                                           List<Diagnostic> diagnostics) {
        object.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                diagnostics.add(Diagnostics.schemaError(Diagnostics.genericChildPath(path),
                        "unknown field", "remove the field or use a supported one"));
            }
        });
    }
}
