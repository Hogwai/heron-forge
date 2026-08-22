package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.Severity;
import java.util.List;
import java.util.Objects;

/** Immutable result of validating an application configuration. */
public record ValidationReport(List<Diagnostic> diagnostics) {

    /**
     * Creates a validation report with an immutable defensive copy of its
     * diagnostics.
     *
     * @param diagnostics the validation diagnostics
     */
    public ValidationReport {
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Returns whether validation produced no error diagnostics.
     *
     * @return {@code true} when all diagnostics are non-errors
     */
    public boolean valid() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }
}
