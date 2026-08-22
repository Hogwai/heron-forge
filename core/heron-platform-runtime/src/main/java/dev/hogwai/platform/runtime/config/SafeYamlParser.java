package dev.hogwai.platform.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hogwai.platform.runtime.config.mapping.ConfigMapper;
import dev.hogwai.platform.spi.Diagnostic;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.schema.JsonSchema;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Safe YAML parser for application configuration.
 *
 * <p>SnakeYAML Engine performs the lexical parsing and {@link YamlEventValidator}
 * enforces the event-level security policy. Explicit YAML tags (including
 * standard tags such as {@code !!str}) are rejected; no tags are interpreted as
 * application types. Numeric scalars are converted from their event text so
 * that they reach Jackson as exact {@code long}/{@code BigDecimal} values.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class SafeYamlParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Parses a configuration document using the default limits.
     *
     * @param input the input stream
     * @return the parsed application with any diagnostics
     */
    public ParsedApplication parse(InputStream input) {
        return parse(input, YamlLimits.defaults());
    }

    /**
     * Parses a configuration document with explicit limits.
     *
     * @param input the input stream
     * @param limits the limits to apply
     * @return the parsed application with any diagnostics
     */
    public ParsedApplication parse(InputStream input, YamlLimits limits) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        byte[] bytes = readBounded(input, limits, diagnostics);
        if (!diagnostics.isEmpty()) {
            return invalid(diagnostics);
        }
        if (bytes.length == 0) {
            diagnostics.add(Diagnostics.parseError(null, "configuration document is empty",
                    "provide a non-empty YAML document"));
            return invalid(diagnostics);
        }

        LoadSettings settings = LoadSettings.builder()
                .setCodePointLimit(limits.maxBytes())
                .setMaxAliasesForCollections(0)
                .setAllowDuplicateKeys(false)
                .setAllowRecursiveKeys(false)
                .setAllowNonScalarKeys(false)
                .setSchema(new JsonSchema())
                .build();
        Object value;
        try {
            YamlEventValidator validator = new YamlEventValidator(limits);
            if (!validator.validate(new Parse(settings).parseInputStream(new ByteArrayInputStream(bytes)), diagnostics)) {
                return invalid(diagnostics);
            }
            value = validator.value();
        } catch (YamlEngineException _) {
            return parseFailure(diagnostics);
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.valueToTree(value);
        } catch (IllegalArgumentException _) {
            return parseFailure(diagnostics);
        }
        // Deliberately outside the parser/conversion catch: mapper failures are
        // application-schema failures and must not be disguised as YAML errors.
        return ConfigMapper.mapApplication(root);
    }

    private static ParsedApplication parseFailure(List<Diagnostic> diagnostics) {
        diagnostics.add(Diagnostics.parseError(null, "configuration could not be parsed",
                "provide well-formed YAML"));
        return invalid(diagnostics);
    }

    private static ParsedApplication invalid(List<Diagnostic> diagnostics) {
        return new ParsedApplication(null, List.copyOf(diagnostics));
    }

    private byte[] readBounded(InputStream input, YamlLimits limits, List<Diagnostic> diagnostics) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long max = limits.maxBytes();
            long total = 0;
            int read;
            while ((read = input.read(buffer, 0, readLimit(buffer.length, max, total))) != -1) {
                total += read;
                if (total > max) {
                    diagnostics.add(Diagnostics.parseError(null, "configuration exceeds maximum size",
                            "reduce the configuration size"));
                    return new byte[0];
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException _) {
            diagnostics.add(Diagnostics.parseError(null, "failed to read configuration",
                    "provide a readable configuration"));
            return new byte[0];
        }
    }

    private static int readLimit(int bufferLength, long max, long total) {
        long remaining = max + 1 - total;
        if (remaining <= 0) {
            return 0;
        }
        return (int) Math.min(bufferLength, remaining);
    }
}
