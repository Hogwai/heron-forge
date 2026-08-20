package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.load.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a user-provided nested key (the sentinel) never leaks into any
 * diagnostic path, message or remediation, regardless of which parser control
 * rejects the document.
 */
class SafeYamlParserSentinelTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();
    private static final String SENTINEL = "SENTINEL_KEY_XYZ";

    private static ParsedApplication parse(String yaml) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), YamlLimits.defaults());
    }

    private static ParsedApplication parse(String yaml, YamlLimits limits) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), limits);
    }

    private static void assertSentinelAbsent(ParsedApplication result) {
        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics()).isNotEmpty();
        for (Diagnostic d : result.diagnostics()) {
            assertThat(d.path()).doesNotContain(SENTINEL);
            assertThat(d.message()).doesNotContain(SENTINEL);
            assertThat(d.remediation()).doesNotContain(SENTINEL);
        }
    }

    @Test
    void customTagOnSentinelKeyDoesNotLeakSentinel() {
        ParsedApplication result = parse(SENTINEL + ": !custom value\n");
        assertSentinelAbsent(result);
    }

    @Test
    void depthOverflowWithSentinelKeyDoesNotLeakSentinel() {
        YamlLimits shallow = new YamlLimits(1000, 3, 100, 100);
        ParsedApplication result = parse("a:\n  b:\n    c:\n      d:\n        " + SENTINEL + ": 1\n", shallow);
        assertSentinelAbsent(result);
    }

    @Test
    void nodeOverflowWithSentinelKeyDoesNotLeakSentinel() {
        YamlLimits fewNodes = new YamlLimits(1000, 20, 3, 100);
        ParsedApplication result = parse(SENTINEL + ": 1\na: 2\nb: 3\nc: 4\n", fewNodes);
        assertSentinelAbsent(result);
    }

    @Test
    void duplicateSentinelKeyDoesNotLeakSentinel() {
        ParsedApplication result = parse(SENTINEL + ": 1\n" + SENTINEL + ": 2\n");
        assertSentinelAbsent(result);
    }
}
