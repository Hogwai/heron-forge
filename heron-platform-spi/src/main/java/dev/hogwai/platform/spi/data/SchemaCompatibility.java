package dev.hogwai.platform.spi.data;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Directional schema compatibility checks.
 *
 * <p>{@link #check(Schema, Schema)} verifies that an {@code output} schema can
 * safely feed an {@code input} schema: every required input field must exist in
 * the output, types must be compatible without implicit numeric widening,
 * nullability must be respected, extra output fields are only allowed when the
 * input is open, an open output cannot feed a closed input, and closed enums
 * must not add symbols on the output side. Framework-independent and immutable.
 */
public final class SchemaCompatibility {

    private SchemaCompatibility() {
    }

    /**
     * An edge in a transitive compatibility chain.
     *
     * @param path   the non-blank edge path
     * @param output the producing schema
     * @param input  the consuming schema
     */
    public record SchemaEdge(String path, Schema output, Schema input) {
        /**
         * Compact constructor enforcing the edge contract.
         *
         * @throws NullPointerException     if {@code path}, {@code output} or
         *                                  {@code input} is {@code null}
         * @throws IllegalArgumentException if {@code path} is blank
         */
        public SchemaEdge {
            Objects.requireNonNull(path, "path must not be null");
            if (path.isBlank()) {
                throw new IllegalArgumentException("path must not be blank");
            }
            Objects.requireNonNull(output, "output must not be null");
            Objects.requireNonNull(input, "input must not be null");
        }
    }

    /**
     * Checks whether the output schema is compatible with the input schema.
     *
     * @param output the producing schema
     * @param input  the consuming schema
     * @return the compatibility result
     * @throws NullPointerException if {@code output} or {@code input} is
     *                              {@code null}
     */
    public static SchemaCompatibilityResult check(Schema output, Schema input) {
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(input, "input must not be null");
        List<Diagnostic> diagnostics = new ArrayList<>();
        checkInputFieldPresenceAndCompatibility(output, input, diagnostics);
        checkClosedInputRule(output, input, diagnostics);
        return SchemaCompatibilityResult.of(diagnostics);
    }

    /**
     * Checks the presence and compatibility of every input field against the
     * output schema, appending diagnostics in input field order.
     *
     * @param output      the producing schema
     * @param input       the consuming schema
     * @param diagnostics the diagnostics accumulator
     */
    private static void checkInputFieldPresenceAndCompatibility(Schema output, Schema input,
            List<Diagnostic> diagnostics) {
        for (Field inputField : input.fields()) {
            Optional<Field> outputFieldOpt = output.field(inputField.id());
            if (outputFieldOpt.isEmpty()) {
                if (inputField.isRequired()) {
                    diagnostics.add(Diagnostic.of(PlatformErrorCode.SCHEMA_INCOMPATIBLE, Severity.ERROR,
                            "required input field missing in output: " + inputField.id()));
                }
                continue;
            }
            checkFieldCompatibility(outputFieldOpt.get(), inputField, diagnostics);
        }
    }

    /**
     * Checks the type and nullability compatibility of a single input field
     * against its output counterpart, appending diagnostics in that order.
     *
     * @param outputField the producing field
     * @param inputField  the consuming field
     * @param diagnostics the diagnostics accumulator
     */
    private static void checkFieldCompatibility(Field outputField, Field inputField, List<Diagnostic> diagnostics) {
        if (!FieldTypes.compatible(outputField.type(), inputField.type())) {
            diagnostics.add(Diagnostic.of(PlatformErrorCode.SCHEMA_INCOMPATIBLE, Severity.ERROR,
                    "incompatible type for field: " + inputField.id()));
        }
        if (!inputField.nullable() && outputField.nullable()) {
            diagnostics.add(Diagnostic.of(PlatformErrorCode.SCHEMA_INCOMPATIBLE, Severity.ERROR,
                    "output field is nullable but input requires non-null: " + inputField.id()));
        }
    }

    /**
     * Checks the closed-input rule: extra output fields are rejected and an
     * open output cannot feed a closed input. Appends diagnostics in output
     * field order, then the open-output diagnostic.
     *
     * @param output      the producing schema
     * @param input       the consuming schema
     * @param diagnostics the diagnostics accumulator
     */
    private static void checkClosedInputRule(Schema output, Schema input, List<Diagnostic> diagnostics) {
        if (input.openFields()) {
            return;
        }
        output.fields().stream()
                .filter(outputField -> input.field(outputField.id()).isEmpty())
                .forEach(outputField -> diagnostics.add(Diagnostic.of(PlatformErrorCode.SCHEMA_INCOMPATIBLE,
                        Severity.ERROR, "extra output field not allowed by closed input: " + outputField.id())));
        if (output.openFields()) {
            diagnostics.add(Diagnostic.of(PlatformErrorCode.SCHEMA_INCOMPATIBLE, Severity.ERROR,
                    "open output cannot feed closed input"));
        }
    }

    /**
     * Checks each edge output-&gt;input in order, returning all failures with
     * their edge path.
     *
     * @param edges the ordered list of edges
     * @return the compatibility result
     * @throws NullPointerException if {@code edges} or any edge is {@code null}
     */
    public static SchemaCompatibilityResult checkTransitive(List<SchemaEdge> edges) {
        Objects.requireNonNull(edges, "edges must not be null");
        List<Diagnostic> diagnostics = edges.stream()
                .flatMap(edge -> check(edge.output(), edge.input()).diagnostics().stream()
                        .map(d -> new Diagnostic(d.code(), d.severity(), edge.path(),
                                d.message(), d.remediation())))
                .toList();
        return SchemaCompatibilityResult.of(diagnostics);
    }
}
