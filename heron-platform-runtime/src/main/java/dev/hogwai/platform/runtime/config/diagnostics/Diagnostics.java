package dev.hogwai.platform.runtime.config.diagnostics;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;
import java.util.Set;

/**
 * Cohesive gate for building diagnostics and safe structural YAML paths.
 *
 * <p>Diagnostics are created with a stable error code and a static message and
 * remediation; user-provided keys and values are never copied into a path.
 * Paths are built from a known root plus either a fixed structural schema
 * segment or a generic {@code <key>} segment.
 */
public final class Diagnostics {

    private static final String GENERIC_SEGMENT = "<key>";

    /**
     * Fixed allowlist of structural schema segments that are safe to keep in a
     * diagnostic path. Any other key is user-provided and must be replaced by
     * the generic {@code <key>} segment.
     */
    private static final Set<String> STRUCTURAL_SEGMENTS = Set.of(
            "apiVersion", "kind", "metadata", "name", "spec", "capabilities",
            "id", "type", "provider", "version", "config", "inputs", "capability", "port");

    private Diagnostics() {
        // no instances
    }

    /**
     * Creates a parse-error diagnostic.
     *
     * @param path        the structural path, or {@code null}
     * @param message     the static message
     * @param remediation the remediation hint
     * @return the diagnostic
     */
    public static Diagnostic parseError(String path, String message, String remediation) {
        return new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, normalize(path), message, remediation);
    }

    /**
     * Creates a schema-error diagnostic.
     *
     * @param path        the structural path, or {@code null}
     * @param message     the static message
     * @param remediation the remediation hint
     * @return the diagnostic
     */
    public static Diagnostic schemaError(String path, String message, String remediation) {
        return new Diagnostic(PlatformErrorCode.CONFIG_SCHEMA_ERROR, Severity.ERROR, normalize(path), message, remediation);
    }

    /**
     * Appends a segment to a parent path. Structural schema fields keep their
     * name; any other (user-provided) key is replaced by the generic
     * {@code <key>} segment so it is never copied into a diagnostic.
     *
     * @param parent  the parent path, or {@code ""} for the root
     * @param segment the field name
     * @return the child path
     */
    public static String childPath(String parent, String segment) {
        if (STRUCTURAL_SEGMENTS.contains(segment)) {
            return parent.isEmpty() ? "/" + segment : parent + "/" + segment;
        }
        return genericChildPath(parent);
    }

    /**
     * Appends a generic segment to a parent path, used where the real key is
     * user-provided and must not be copied into the diagnostic.
     *
     * @param parent the parent path, or {@code ""} for the root
     * @return the child path with a generic segment
     */
    public static String genericChildPath(String parent) {
        return parent.isEmpty() ? "/" + GENERIC_SEGMENT : parent + "/" + GENERIC_SEGMENT;
    }

    private static String normalize(String path) {
        return (path == null || path.isEmpty()) ? null : path;
    }
}
