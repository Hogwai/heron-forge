package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.load.config.yaml.YamlLimits;
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

    private static ParsedApplication parse(String yaml, YamlLimits limits) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), limits);
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

    @Test
    void maxDepthExactBoundary() {
        String yaml = nestedMapping(3);
        ParsedApplication exact = parse(yaml, new YamlLimits(1000, 3, 100, 100));
        ParsedApplication oneLess = parse(yaml, new YamlLimits(1000, 2, 100, 100));

        assertThat(exact.diagnostics())
                .extracting(Diagnostic::code)
                .doesNotContain(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(oneLess.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void configuredDepthAboveTwentyIsNotSilentlyCapped() {
        ParsedApplication result = parse(nestedMapping(21), new YamlLimits(2000, 100, 100, 100));

        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .doesNotContain(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    private static String nestedMapping(int depth) {
        StringBuilder yaml = new StringBuilder();
        for (int index = 0; index < depth - 1; index++) {
            yaml.append("  ".repeat(index)).append("level").append(index).append(":\n");
        }
        yaml.append("  ".repeat(depth)).append("value: x\n");
        return yaml.toString();
    }
}
