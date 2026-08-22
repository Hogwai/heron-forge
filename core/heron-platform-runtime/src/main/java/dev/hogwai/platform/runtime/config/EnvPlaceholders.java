package dev.hogwai.platform.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.hogwai.platform.spi.Diagnostic;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves environment-variable placeholders in configuration string values.
 *
 * <p>A string value of the exact form {@code ${VAR_NAME}} is replaced by the
 * value of the environment variable {@code VAR_NAME}. Placeholders must span
 * the entire string value; partial interpolation and unknown syntax are
 * rejected so that a mistyped reference can never silently pass through as a
 * literal credential. A missing or blank variable is reported as a diagnostic.
 *
 * <p>Secrets therefore never appear in configuration files: operators inject
 * them through the environment (directly or via a secrets manager), and the
 * runtime resolves them before any mapping occurs.
 */
final class EnvPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private EnvPlaceholders() {
        // no instances
    }

    /**
     * Returns a copy of the tree with every eligible string value resolved
     * against the process environment, appending one diagnostic per rejected
     * reference.
     *
     * @param root        the parsed configuration root
     * @param diagnostics collector for placeholder failures
     * @return the resolved tree (unchanged when nothing matched)
     */
    static JsonNode resolve(JsonNode root, List<Diagnostic> diagnostics) {
        return resolve(root, diagnostics, System.getenv()::get);
    }

    /**
     * Returns a copy of the tree resolved against an explicit lookup, for
     * tests and future pluggable resolvers.
     *
     * @param root        the parsed configuration root
     * @param diagnostics collector for placeholder failures
     * @param lookup      maps a variable name to its value, or {@code null}
     * @return the resolved tree (unchanged when nothing matched)
     */
    static JsonNode resolve(JsonNode root, List<Diagnostic> diagnostics, Function<String, String> lookup) {
        return resolveNode(root, "", diagnostics, lookup);
    }

    private static JsonNode resolveNode(JsonNode node, String path,
                                        List<Diagnostic> diagnostics, Function<String, String> lookup) {
        if (node.isObject()) {
            ObjectNode resolved = NODES.objectNode();
            node.properties().forEach(entry -> resolved.set(entry.getKey(),
                    resolveNode(entry.getValue(), path + "/" + entry.getKey(), diagnostics, lookup)));
            return resolved;
        }
        if (node.isArray()) {
            ArrayNode array = NODES.arrayNode();
            for (int index = 0; index < node.size(); index++) {
                array.add(resolveNode(node.get(index), path + "/" + index, diagnostics, lookup));
            }
            return array;
        }
        if (node.isTextual()) {
            String resolved = resolveText(node.textValue(), path, diagnostics, lookup);
            return NODES.textNode(resolved);
        }
        return node;
    }

    private static String resolveText(String value, String path,
                                      List<Diagnostic> diagnostics, Function<String, String> lookup) {
        if (!value.contains("${")) {
            return value;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.matches()) {
            diagnostics.add(Diagnostics.parseError(path,
                    "environment variable placeholder must span the entire value",
                    "use exactly ${VARIABLE} as the whole value"));
            return value;
        }
        String name = matcher.group(1);
        String resolved = lookup.apply(name);
        if (resolved == null || resolved.isBlank()) {
            diagnostics.add(Diagnostics.parseError(path,
                    "environment variable '" + name + "' is not set",
                    "set " + name + " before starting the application"));
            return value;
        }
        return resolved;
    }
}
