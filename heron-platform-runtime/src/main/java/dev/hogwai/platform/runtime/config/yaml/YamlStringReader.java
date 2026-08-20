package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.util.List;

/**
 * Reads a YAML string value while enforcing length and interpolation limits.
 *
 * <p>Package-private helper that keeps the public
 * {@link dev.hogwai.platform.runtime.config.SafeYamlParser} within the
 * project's cyclomatic complexity budget.
 */
final class YamlStringReader {

    JsonNode parseString(YAMLParser parser, ParseState state, List<Diagnostic> diagnostics, String path)
            throws IOException {
        String text = parser.getText();
        if (text.length() > state.limits.maxStringLength()) {
            diagnostics.add(Diagnostics.parseError(path, "string value exceeds maximum length",
                    "shorten the value"));
            state.failed = true;
            return null;
        }
        if (ForbiddenContent.hasInterpolation(text)) {
            diagnostics.add(Diagnostics.parseError(path, "value contains forbidden interpolation",
                    "remove the ${...} expression"));
            state.failed = true;
            return null;
        }
        return TextNode.valueOf(text);
    }
}
