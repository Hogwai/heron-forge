package dev.hogwai.platform.runtime.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlLimitsTest {

    @Test
    void exposesAllComponents() {
        YamlLimits limits = new YamlLimits(1024, 5, 100, 64);

        assertThat(limits).extracting(YamlLimits::maxBytes, YamlLimits::maxDepth,
                        YamlLimits::maxNodes, YamlLimits::maxStringLength)
                .containsExactly(1024, 5, 100, 64);
    }

    @Test
    void recordEqualityAndHashCode() {
        YamlLimits a = new YamlLimits(1024, 5, 100, 64);
        YamlLimits b = new YamlLimits(1024, 5, 100, 64);
        YamlLimits different = new YamlLimits(2048, 5, 100, 64);

        assertThat(a)
                .isEqualTo(b)
                .hasSameHashCodeAs(b)
                .isNotEqualTo(different)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object());
    }

    @Test
    void defaultsAreStrictlyPositive() {
        YamlLimits defaults = YamlLimits.defaults();

        assertThat(defaults).extracting(YamlLimits::maxBytes, YamlLimits::maxDepth,
                        YamlLimits::maxNodes, YamlLimits::maxStringLength)
                .containsExactly(64 * 1024, 20, 10_000, 4096);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThatThrownBy(() -> new YamlLimits(0, 5, 100, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxBytes must be strictly positive");
        assertThatThrownBy(() -> new YamlLimits(1024, 0, 100, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxDepth must be strictly positive");
        assertThatThrownBy(() -> new YamlLimits(1024, 5, 0, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxNodes must be strictly positive");
        assertThatThrownBy(() -> new YamlLimits(1024, 5, 100, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxStringLength must be strictly positive");
        assertThatThrownBy(() -> new YamlLimits(-1, 5, 100, 64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsAreAppliedByTheParserCallSite() {
        String yaml = """
                apiVersion: heron.dev/v1
                application: x
                capabilities: []
                """;
        SafeYamlParser parser = new SafeYamlParser();

        ParsedApplication accepted = parser.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                new YamlLimits(yaml.getBytes(StandardCharsets.UTF_8).length, 20, 100, 100));
        ParsedApplication rejected = parser.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                new YamlLimits(yaml.getBytes(StandardCharsets.UTF_8).length - 1, 20, 100, 100));

        assertThat(accepted.isValid()).isTrue();
        assertThat(rejected.isValid()).isFalse();
    }
}