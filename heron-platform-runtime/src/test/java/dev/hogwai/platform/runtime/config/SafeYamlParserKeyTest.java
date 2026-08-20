package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SafeYamlParserKeyTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static ParsedApplication parse(String yaml) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), YamlLimits.defaults());
    }

    private static ParsedApplication parse(String yaml, YamlLimits limits) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), limits);
    }

    @Test
    void acceptsIncludeKeyWithoutFalsePositive() {
        // include is no longer treated as forbidden content at parse time; the
        // only rejection is the schema-level unknown-field check.
        ParsedApplication result = parse("include: 1\n");

        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .isNotEmpty()
                .doesNotContain(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsInterpolationKey() {
        ParsedApplication result = parse("${X}: 1\n");

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void acceptsSecretShapedKeyWithoutFalsePositive() {
        // secret-shaped keys are no longer rejected by lexical detection; the
        // only rejection is the schema-level unknown-field check.
        ParsedApplication result = parse("password: 1\n");

        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .isNotEmpty()
                .doesNotContain(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsKeyTooLong() {
        YamlLimits limits = new YamlLimits(1000, 20, 100, 5);
        ParsedApplication result = parse("aaaaaaaaaa: 1\n", limits);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsAnchoredKey() {
        ParsedApplication result = parse("&k key: 1\n");

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void acceptsPlainKeyInNestedConfigWithoutFalsePositive() {
        // secret-shaped config keys are no longer rejected.
        ParsedApplication result = parse("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities:
                    - id: c
                      type: source
                      provider:
                        id: acme
                        version: 1.2.3
                      config:
                        password: secret
                """);

        assertThat(result.isValid()).isTrue();
    }
}
