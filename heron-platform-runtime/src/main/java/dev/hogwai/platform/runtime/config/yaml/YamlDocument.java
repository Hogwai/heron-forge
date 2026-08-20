package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.List;

/**
 * Immutable result of parsing a YAML document.
 *
 * <p>Carries the parsed root tree (when the document is valid) together with
 * any parse diagnostics. When {@link #isValid()} is {@code false}, the root is
 * {@code null}.
 */
public final class YamlDocument {

    private final JsonNode root;
    private final List<Diagnostic> diagnostics;

    /**
     * Creates a parsed document.
     *
     * @param root        the parsed root, or {@code null} if invalid
     * @param diagnostics the diagnostics; copied defensively and exposed immutably
     */
    public YamlDocument(JsonNode root, List<Diagnostic> diagnostics) {
        this.root = root;
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * @return the parsed root tree, or {@code null} if the document is invalid
     */
    public JsonNode root() {
        return root;
    }

    /**
     * @return an immutable view of the diagnostics
     */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * @return {@code true} if the document parsed without errors
     */
    public boolean isValid() {
        return diagnostics.isEmpty();
    }
}
