package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.util.List;

/**
 * Builds a Jackson tree from a YAML token stream while enforcing document
 * boundaries and array depth limits.
 *
 * <p>Package-private helper that keeps the public {@link dev.hogwai.platform.runtime.config.SafeYamlParser}
 * within the project's cyclomatic complexity budget.
 */
final class YamlTreeBuilder {

    JsonNode parseDocument(YAMLParser parser, ParseState state, List<Diagnostic> diagnostics) throws IOException {
        JsonToken token = parser.nextToken();
        if (token == null) {
            diagnostics.add(Diagnostics.parseError(null, "configuration document is empty",
                    "provide a non-empty YAML document"));
            return null;
        }
        if (token != JsonToken.START_OBJECT) {
            diagnostics.add(Diagnostics.parseError(null, "configuration root must be a mapping",
                    "start the document with a mapping such as 'apiVersion: ...'"));
            return null;
        }
        if (new YamlForbiddenCheck().isForbidden(parser, state, null, diagnostics)) {
            return null;
        }
        state.nodeCount++;
        if (state.nodeCount > state.limits.maxNodes()) {
            diagnostics.add(Diagnostics.parseError(null, "configuration exceeds maximum node count",
                    "reduce the number of configuration entries"));
            state.failed = true;
            return null;
        }
        JsonNode root = new YamlObjectBuilder().parseObject(parser, state, diagnostics, "");
        if (root == null) {
            return null;
        }
        if (parser.nextToken() != null) {
            diagnostics.add(Diagnostics.parseError(null, "multiple YAML documents are not supported",
                    "provide a single document"));
            return null;
        }
        return root;
    }

    JsonNode parseArray(YAMLParser parser, ParseState state, List<Diagnostic> diagnostics, String path)
            throws IOException {
        state.depth++;
        if (state.depth > state.limits.maxDepth()) {
            diagnostics.add(Diagnostics.parseError(path, "configuration exceeds maximum nesting depth",
                    "reduce the nesting depth"));
            state.failed = true;
            return null;
        }
        ArrayNode node = JsonNodeFactory.instance.arrayNode();
        int index = 0;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                diagnostics.add(Diagnostics.parseError(path, "unexpected end of configuration",
                        "close all mappings and sequences"));
                state.failed = true;
                return null;
            }
            JsonNode value = new YamlValueReader().parseValue(parser, state, diagnostics, path + "/" + index);
            if (value == null && state.failed) {
                return null;
            }
            node.add(value);
            index++;
        }
        state.depth--;
        return node;
    }
}
