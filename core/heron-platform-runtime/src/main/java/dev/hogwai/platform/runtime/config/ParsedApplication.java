package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.spi.Diagnostic;
import java.util.List;

/**
 * Immutable, framework-independent model of a parsed application configuration.
 *
 * <p>Carries the mapped {@link ApplicationConfig} (when the document is valid)
 * together with any {@link Diagnostic}s produced during parsing or schema
 * validation.
 *
 * <p>This type enforces a strict invariant: either there are no diagnostics and
 * the application is non-{@code null}, or there is at least one diagnostic and
 * the application is {@code null}. {@link #isValid()} reflects that invariant.
 */
public record ParsedApplication(ApplicationConfig application, List<Diagnostic> diagnostics) {

    /**
     * Creates a parsed application.
     *
     * <p>The application must be non-{@code null} exactly when the diagnostics
     * list is empty.
     *
     * @param application the mapped application, or {@code null} if invalid
     * @param diagnostics the diagnostics; copied defensively and exposed immutably
     * @throws IllegalArgumentException if the invariant is violated (a
     *                                  non-{@code null} application with
     *                                  diagnostics, or a {@code null}
     *                                  application without diagnostics)
     */
    public ParsedApplication {
        List<Diagnostic> copy = List.copyOf(diagnostics);
        if (copy.isEmpty() && application == null) {
            throw new IllegalArgumentException("application must not be null when there are no diagnostics");
        }
        if (!copy.isEmpty() && application != null) {
            throw new IllegalArgumentException("application must be null when there are diagnostics");
        }
        diagnostics = copy;
    }

    /**
     * @return {@code true} if the document parsed and validated without errors
     */
    public boolean isValid() {
        return application != null;
    }
}