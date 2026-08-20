package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.hogwai.platform.runtime.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SafeYamlParserNumberTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static ParsedApplication parse(String yaml) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), YamlLimits.defaults());
    }

    private static String capabilityWithConfig(String configBody) {
        return """
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
                """ + configBody;
    }

    @Test
    void parsesLongMaxValue() {
        ParsedApplication result = parse(capabilityWithConfig("        big: 9223372036854775807\n"));

        assertThat(result.isValid()).isTrue();
        Map<String, Object> config = result.application().capabilities().get(0).config();
        assertThat(config).containsEntry("big", Long.MAX_VALUE);
    }

    @Test
    void parsesBigDecimalValue() {
        ParsedApplication result = parse(capabilityWithConfig("        ratio: 1.5\n"));

        assertThat(result.isValid()).isTrue();
        Map<String, Object> config = result.application().capabilities().get(0).config();
        assertThat(config).containsEntry("ratio", new BigDecimal("1.5"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedNumbers")
    void rejectsInvalidNumberAtPath(String caseName, String configBody) {
        ParsedApplication result = parse(capabilityWithConfig(configBody));

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_PARSE_ERROR,
                        "/spec/capabilities/0/config/<key>"));
    }

    private static Stream<Arguments> rejectedNumbers() {
        return Stream.of(
                Arguments.of("rejectsLongOverflowAtPath", "        big: 9223372036854775808\n"),
                Arguments.of("rejectsInfinityAtPath", "        ratio: .inf\n"),
                Arguments.of("rejectsNaNValueAtPath", "        ratio: .NaN\n"),
                Arguments.of("rejectsBigDecimalScaleOverflowAtPath", "        ratio: 1.0e-2147483649\n"));
    }
}