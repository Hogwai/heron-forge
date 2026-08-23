package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.spi.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a {@link SchemaCompatibility} check.
 *
 */
public final class SchemaCompatibilityResult {

    private final List<Diagnostic> diagnostics;
    private final boolean compatible;

    private SchemaCompatibilityResult(List<Diagnostic> diagnostics) {
        this.diagnostics = List.copyOf(diagnostics);
        this.compatible = diagnostics.isEmpty();
    }

    /**
     * Creates a result from the given diagnostics.
     *
     * @param diagnostics the diagnostics; copied defensively
     * @return the result
     * @throws NullPointerException if {@code diagnostics} is {@code null}
     */
    public static SchemaCompatibilityResult of(List<Diagnostic> diagnostics) {
        return new SchemaCompatibilityResult(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }

    /**
     * Returns whether no incompatibility was found.
     *
     * @return {@code true} if no incompatibility was found
     */
    public boolean compatible() {
        return compatible;
    }

    /**
     * Returns an immutable view of the diagnostics.
     *
     * @return an immutable view of the diagnostics
     */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }
}
