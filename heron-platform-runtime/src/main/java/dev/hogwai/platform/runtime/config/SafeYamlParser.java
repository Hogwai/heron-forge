package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.runtime.config.mapping.ConfigMapper;
import dev.hogwai.platform.runtime.config.yaml.YamlDocument;
import dev.hogwai.platform.runtime.config.yaml.YamlDocumentParser;
import dev.hogwai.platform.runtime.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Safe YAML parser for application configuration.
 *
 * <p>Reads a bounded document, parses it into a Jackson tree while enforcing
 * strict duplicate-key detection, depth and node limits, and rejection of
 * custom tags, anchors, aliases, multiple documents and forbidden scalar
 * content, then maps the tree into an immutable {@link ParsedApplication}.
 * Default typing is never enabled and no provider-specific Java types are
 * deserialized. All violations are converted into public {@link Diagnostic}s
 * rather than leaking Jackson exceptions.
 */
public final class SafeYamlParser {

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
     * @param input  the input stream
     * @param limits the limits to apply
     * @return the parsed application with any diagnostics
     */
    public ParsedApplication parse(InputStream input, YamlLimits limits) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        byte[] bytes = readBounded(input, limits, diagnostics);
        if (bytes.length == 0) {
            return new ParsedApplication(null, List.copyOf(diagnostics));
        }
        YamlDocument document = YamlDocumentParser.parse(bytes, limits);
        if (!document.isValid()) {
            return new ParsedApplication(null, document.diagnostics());
        }
        return ConfigMapper.mapApplication(document.root());
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
