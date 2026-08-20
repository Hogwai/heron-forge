package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Reads YAML numeric values as canonical v1 scalars: {@code long} for integers
 * and {@link BigDecimal} for decimals.
 *
 * <p>All relevant number errors, including integer overflow, malformed decimals
 * ({@code .inf}, {@code .NaN}), and BigDecimal scale/precision overflow, are
 * converted into a public {@code CONFIG_PARSE_ERROR} diagnostic at the current
 * path rather than leaking a raw Jackson exception or falling through to the
 * global catch with a {@code null} path. Package-private helper that keeps the
 * public {@link dev.hogwai.platform.runtime.config.SafeYamlParser} within the project's cyclomatic complexity
 * budget.
 */
final class YamlNumberReader {

    JsonNode parseInteger(YAMLParser parser, ParseState state, String path, List<Diagnostic> diagnostics)
            throws IOException {
        try {
            return LongNode.valueOf(parser.getLongValue());
        } catch (IOException e) {
            diagnostics.add(Diagnostics.parseError(path, "integer value exceeds supported range",
                    "use a value within the 64-bit signed range"));
            state.failed = true;
            return null;
        }
    }

    JsonNode parseDecimal(YAMLParser parser, ParseState state, String path, List<Diagnostic> diagnostics)
            throws IOException {
        try {
            BigDecimal value = parser.getDecimalValue();
            return DecimalNode.valueOf(value);
        } catch (IOException e) {
            diagnostics.add(Diagnostics.parseError(path, "decimal value is not supported",
                    "use a supported decimal value"));
            state.failed = true;
            return null;
        }
    }
}
