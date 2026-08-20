package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Validates a YAML mapping key with the same security controls applied to
 * values: bounded length, interpolation and, when exposed, custom tags,
 * anchors and aliases.
 *
 * <p>Package-private helper that keeps the public
 * {@link dev.hogwai.platform.runtime.config.SafeYamlParser} within the
 * project's cyclomatic complexity budget.
 */
final class YamlKeyCheck {

    private static final Set<String> SAFE_TAGS = Set.of("str", "int", "float", "bool", "null", "map", "seq");

    boolean isForbidden(YAMLParser parser, ParseState state, String key, String path, List<Diagnostic> diagnostics)
            throws IOException {
        if (key.length() > state.limits.maxStringLength()) {
            diagnostics.add(Diagnostics.parseError(path, "mapping key exceeds maximum length",
                    "shorten the key"));
            state.failed = true;
            return true;
        }
        if (ForbiddenContent.hasInterpolation(key)) {
            diagnostics.add(Diagnostics.parseError(path, "mapping key contains forbidden interpolation",
                    "remove the ${...} expression"));
            state.failed = true;
            return true;
        }
        String typeId = parser.getTypeId();
        if (typeId != null && !SAFE_TAGS.contains(typeId)) {
            diagnostics.add(Diagnostics.parseError(path, "custom YAML tag on key is not allowed",
                    "remove the custom tag"));
            state.failed = true;
            return true;
        }
        if (parser.isCurrentAlias()) {
            diagnostics.add(Diagnostics.parseError(path, "YAML alias on key is not allowed",
                    "replace the alias with its value"));
            state.failed = true;
            return true;
        }
        if (parser.getCurrentAnchor() != null) {
            diagnostics.add(Diagnostics.parseError(path, "YAML anchor on key is not allowed",
                    "remove the anchor"));
            state.failed = true;
            return true;
        }
        return false;
    }
}
