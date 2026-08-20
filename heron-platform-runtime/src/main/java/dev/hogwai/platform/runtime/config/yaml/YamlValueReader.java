package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.util.List;

/**
 * Reads a single YAML value into a Jackson node while enforcing node limits,
 * custom-tag, anchor and alias rejection.
 *
 * <p>Package-private helper that keeps the public {@link dev.hogwai.platform.runtime.config.SafeYamlParser}
 * within the project's cyclomatic complexity budget.
 */
final class YamlValueReader {

    JsonNode parseValue(YAMLParser parser, ParseState state, List<Diagnostic> diagnostics, String path)
            throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            return null;
        }
        state.nodeCount++;
        if (state.nodeCount > state.limits.maxNodes()) {
            diagnostics.add(Diagnostics.parseError(path, "configuration exceeds maximum node count",
                    "reduce the number of configuration entries"));
            state.failed = true;
            return null;
        }
        // The path already carries a generic <key> segment for any
        // non-structural (user-provided) key, so value-level violations never
        // copy a raw user key into the diagnostic.
        if (new YamlForbiddenCheck().isForbidden(parser, state, path, diagnostics)) {
            return null;
        }
        switch (token) {
            case START_OBJECT:
                return new YamlObjectBuilder().parseObject(parser, state, diagnostics, path);
            case START_ARRAY:
                return new YamlTreeBuilder().parseArray(parser, state, diagnostics, path);
            case VALUE_STRING:
                return new YamlStringReader().parseString(parser, state, diagnostics, path);
            case VALUE_NUMBER_INT:
                return new YamlNumberReader().parseInteger(parser, state, path, diagnostics);
            case VALUE_NUMBER_FLOAT:
                return new YamlNumberReader().parseDecimal(parser, state, path, diagnostics);
            case VALUE_TRUE:
                return BooleanNode.TRUE;
            case VALUE_FALSE:
                return BooleanNode.FALSE;
            case VALUE_NULL:
                return NullNode.getInstance();
            default:
                diagnostics.add(Diagnostics.parseError(path, "unsupported value type",
                        "use a scalar, list or mapping"));
                state.failed = true;
                return null;
        }
    }
}
