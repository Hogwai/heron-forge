package dev.hogwai.platform.runtime.config.yaml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cohesive public gate that parses a YAML document into an immutable
 * {@link YamlDocument}.
 *
 * <p>This is the single crossing point from the public
 * {@link dev.hogwai.platform.runtime.config.SafeYamlParser} into the
 * package-private YAML token/tree parsing implementation. It takes stable
 * inputs (bytes and {@link YamlLimits}) and returns an immutable result; it
 * never exposes the underlying {@link YAMLParser} or a mutable diagnostics
 * collector.
 */
public final class YamlDocumentParser {

    private YamlDocumentParser() {
        // no instances
    }

    /**
     * Parses a YAML document into an immutable result.
     *
     * @param bytes  the document bytes
     * @param limits the limits to apply
     * @return the parsed document with any diagnostics
     */
    public static YamlDocument parse(byte[] bytes, YamlLimits limits) {
        List<dev.hogwai.platform.spi.Diagnostic> diagnostics = new ArrayList<>();
        YAMLFactory factory = new YAMLFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try (YAMLParser parser = factory.createParser(bytes)) {
            JsonNode root = new YamlTreeBuilder().parseDocument(parser, new ParseState(limits), diagnostics);
            return new YamlDocument(root, diagnostics);
        } catch (IOException e) {
            diagnostics.add(Diagnostics.parseError(null, "configuration could not be parsed",
                    "provide well-formed YAML"));
            return new YamlDocument(null, diagnostics);
        }
    }
}
