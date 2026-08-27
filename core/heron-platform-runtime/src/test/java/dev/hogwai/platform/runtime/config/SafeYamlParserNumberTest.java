package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SafeYamlParserNumberTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static ParsedApplication parse(String yaml) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), YamlLimits.defaults());
    }

    private static String capabilityWithConfig(String configBody) {
        return """
                apiVersion: heron.dev/v1
                application: x
                capabilities:
                  - id: c
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
        Map<String, Object> config = result.application().capabilities().getFirst().config();
        assertThat(config).containsEntry("big", Long.MAX_VALUE);
    }

    @Test
    void parsesBigDecimalValue() {
        ParsedApplication result = parse(capabilityWithConfig("        ratio: 1.5\n"));

        assertThat(result.isValid()).isTrue();
        Map<String, Object> config = result.application().capabilities().getFirst().config();
        assertThat(config).containsEntry("ratio", new BigDecimal("1.5"));
    }

    @Test
    void preservesExactDecimalLexicalValue() {
        String precise = "0.123456789012345678901234567890123456789";
        ParsedApplication result = parse(capabilityWithConfig("        ratio: " + precise + "\n"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.application().capabilities().getFirst().config())
                .containsEntry("ratio", new BigDecimal(precise));
    }

    @Test
    void preservesVerySmallAndLargeExponents() {
        ParsedApplication result = parse(capabilityWithConfig(
                "        tiny: 1e-400\n        huge: 1e309\n        quoted: \"1e-400\"\n"));

        assertThat(result.isValid()).isTrue();
        Map<String, Object> config = result.application().capabilities().getFirst().config();
        assertThat(config).containsEntry("tiny", new BigDecimal("1e-400"))
                .containsEntry("huge", new BigDecimal("1e309"))
                .containsEntry("quoted", "1e-400");
    }

    @Test
    void preservesLongBounds() {
        ParsedApplication result = parse(capabilityWithConfig(
                """
                                minimum: -9223372036854775808
                                maximum: 9223372036854775807
                        """));

        assertThat(result.isValid()).isTrue();
        assertThat(result.application().capabilities().getFirst().config())
                .containsEntry("minimum", Long.MIN_VALUE)
                .containsEntry("maximum", Long.MAX_VALUE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedNumbers")
    void rejectsInvalidNumberAtPath(String caseName, String configBody) {
        ParsedApplication result = parse(capabilityWithConfig(configBody));

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_PARSE_ERROR,
                        "/capabilities/0/config/<key>"));
    }

    private static Stream<Arguments> rejectedNumbers() {
        return Stream.of(
                Arguments.of("rejectsLongOverflowAtPath", "        big: 9223372036854775808\n"),
                Arguments.of("rejectsInfinityAtPath", "        ratio: .inf\n"),
                Arguments.of("rejectsNaNValueAtPath", "        ratio: .NaN\n"),
                Arguments.of("rejectsBigDecimalScaleOverflowAtPath", "        ratio: 1.0e-2147483649\n"));
    }
}
