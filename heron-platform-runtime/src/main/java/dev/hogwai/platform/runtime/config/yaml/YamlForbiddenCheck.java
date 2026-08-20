package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Rejects custom tags, anchors and aliases on a YAML value.
 *
 * <p>Package-private helper that keeps the public {@link dev.hogwai.platform.runtime.config.SafeYamlParser}
 * within the project's cyclomatic complexity budget.
 */
final class YamlForbiddenCheck {

    private static final Set<String> SAFE_TAGS = Set.of("str", "int", "float", "bool", "null", "map", "seq");

    boolean isForbidden(YAMLParser parser, ParseState state, String path, List<Diagnostic> diagnostics)
            throws IOException {
        String typeId = parser.getTypeId();
        if (typeId != null && !SAFE_TAGS.contains(typeId)) {
            diagnostics.add(Diagnostics.parseError(path, "custom YAML tag is not allowed",
                    "remove the custom tag"));
            state.failed = true;
            return true;
        }
        if (parser.isCurrentAlias()) {
            diagnostics.add(Diagnostics.parseError(path, "YAML aliases are not allowed",
                    "replace the alias with its value"));
            state.failed = true;
            return true;
        }
        if (parser.getCurrentAnchor() != null) {
            diagnostics.add(Diagnostics.parseError(path, "YAML anchors are not allowed",
                    "remove the anchor"));
            state.failed = true;
            return true;
        }
        return false;
    }
}
