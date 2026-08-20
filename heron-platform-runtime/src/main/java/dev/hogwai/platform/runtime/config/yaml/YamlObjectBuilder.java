package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a Jackson object node from a YAML token stream while enforcing
 * duplicate-key detection, key security controls and depth limits.
 *
 * <p>Package-private helper that keeps the public {@link dev.hogwai.platform.runtime.config.SafeYamlParser}
 * within the project's cyclomatic complexity budget.
 */
final class YamlObjectBuilder {

    private static final Pattern DUPLICATE_FIELD =
            Pattern.compile("Duplicate field '([^']*)'");

    JsonNode parseObject(YAMLParser parser, ParseState state, List<Diagnostic> diagnostics, String path)
            throws IOException {
        state.depth++;
        if (state.depth > state.limits.maxDepth()) {
            diagnostics.add(Diagnostics.parseError(path, "configuration exceeds maximum nesting depth",
                    "reduce the nesting depth"));
            state.failed = true;
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        Set<String> seen = new HashSet<>();
        JsonToken token;
        while ((token = nextFieldToken(parser, state, path, diagnostics)) != JsonToken.END_OBJECT) {
            if (token == null) {
                return null;
            }
            if (token != JsonToken.FIELD_NAME) {
                diagnostics.add(Diagnostics.parseError(path, "expected a mapping key",
                        "check the mapping structure"));
                state.failed = true;
                return null;
            }
            String fieldName = parser.currentName();
            if (new YamlKeyCheck().isForbidden(parser, state, fieldName, path, diagnostics)) {
                return null;
            }
            String fieldPath = Diagnostics.childPath(path, fieldName);
            if (!seen.add(fieldName)) {
                diagnostics.add(Diagnostics.parseError(fieldPath, "duplicate key in mapping",
                        "remove the duplicate key"));
                state.failed = true;
                return null;
            }
            if (parser.nextToken() == null) {
                diagnostics.add(Diagnostics.parseError(fieldPath, "missing value for mapping key",
                        "provide a value"));
                state.failed = true;
                return null;
            }
            JsonNode value = new YamlValueReader().parseValue(parser, state, diagnostics, fieldPath);
            if (value == null) {
                return null;
            }
            node.set(fieldName, value);
        }
        state.depth--;
        return node;
    }

    private JsonToken nextFieldToken(YAMLParser parser, ParseState state, String path, List<Diagnostic> diagnostics)
            throws IOException {
        try {
            return parser.nextToken();
        } catch (JsonParseException e) {
            Matcher matcher = DUPLICATE_FIELD.matcher(e.getMessage());
            if (matcher.find()) {
                String fieldName = matcher.group(1);
                diagnostics.add(Diagnostics.parseError(Diagnostics.childPath(path, fieldName),
                        "duplicate key in mapping", "remove the duplicate key"));
                state.failed = true;
                return null;
            }
            throw e;
        }
    }
}
