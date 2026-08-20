package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SafeYamlParserLimitTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static final String VALID = """
        apiVersion: platform.dev/v1alpha1
        kind: Application
        metadata:
          name: x
        spec:
          capabilities: []
        """;

    private static ParsedApplication parse(YamlLimits limits) {
        return PARSER.parse(new ByteArrayInputStream(SafeYamlParserLimitTest.VALID.getBytes(StandardCharsets.UTF_8)), limits);
    }

    @Test
    void maxBytesExactBoundary() {
        int length = VALID.getBytes(StandardCharsets.UTF_8).length;

        ParsedApplication exact = parse(new YamlLimits(length, 20, 100, 100));
        ParsedApplication oneLess = parse(new YamlLimits(length - 1, 20, 100, 100));

        assertThat(exact.isValid()).isTrue();
        assertThat(oneLess.isValid()).isFalse();
        assertThat(oneLess.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void maxNodesExactBoundary() {
        // Root + apiVersion + kind + metadata + name + spec + capabilities = 7 nodes.
        ParsedApplication exact = parse(new YamlLimits(1000, 20, 7, 100));
        ParsedApplication oneLess = parse(new YamlLimits(1000, 20, 6, 100));

        assertThat(exact.isValid()).isTrue();
        assertThat(oneLess.isValid()).isFalse();
        assertThat(oneLess.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }
}
