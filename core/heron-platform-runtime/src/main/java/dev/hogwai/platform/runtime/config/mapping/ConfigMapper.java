package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.Diagnostics;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.safe.SafeConfig;
import dev.hogwai.platform.spi.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cohesive public gate that maps a parsed application tree into an immutable
 * {@link ParsedApplication}.
 *
 * <p>This is the single crossing point from the public
 * {@link dev.hogwai.platform.runtime.config.SafeYamlParser} and
 * configuration boundary into the package-private application/provider/capability/input mapping and shape
 * validation below.
 */
public final class ConfigMapper {

    private ConfigMapper() {
        // no instances
    }

    /**
     * Validates a parsed root node and maps it to an application configuration.
     *
     * @param root the parsed root node
     * @return the parsed application with any schema diagnostics
     */
    public static ParsedApplication mapApplication(JsonNode root) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (root == null || !root.isObject()) {
            diagnostics.add(Diagnostics.schemaError(null, "configuration root must be a mapping",
                    "provide a mapping"));
            return new ParsedApplication(null, List.copyOf(diagnostics));
        }
        ApplicationConfig application = ApplicationMapper.map(root, diagnostics);
        return new ParsedApplication(application, List.copyOf(diagnostics));
    }

    static Map<String, Object> mapConfig(JsonNode configNode, String path, List<Diagnostic> diagnostics) {
        if (configNode == null) {
            return Map.of();
        }
        if (!configNode.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path, "config must be a mapping", "provide a mapping"));
            return Map.of();
        }
        try {
            return SafeConfig.fromJsonNode(configNode);
        } catch (IllegalArgumentException _) {
            diagnostics.add(Diagnostics.schemaError(path, "unsupported config value",
                    "use supported scalar, list or mapping values"));
            return Map.of();
        }
    }
}
