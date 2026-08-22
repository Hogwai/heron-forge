package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.spi.Diagnostic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMapperSecretTest {

    private static final YAMLMapper MAPPER = new YAMLMapper();

    private static ParsedApplication validate(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        return ConfigMapper.mapApplication(root);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonReemittedValues")
    void doesNotReemitSensitiveValues(String caseName, String yaml, String forbidden) throws Exception {
        ParsedApplication result = validate(yaml);

        assertThat(result.isValid()).isFalse();
        for (Diagnostic d : result.diagnostics()) {
            assertThat(d.message()).doesNotContain(forbidden);
        }
    }

    private static Stream<Arguments> nonReemittedValues() {
        return Stream.of(
                Arguments.of("duplicateIdNotReemitted", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider:
                              id: acme
                              version: 1.2.3
                          - id: c
                            provider:
                              id: acme
                              version: 1.2.3
                        """, "duplicate capability id c"),
                Arguments.of("unsupportedTypeNotReemitted", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            type: sink
                            provider:
                              id: acme
                              version: 1.2.3
                        """, "sink"),
                Arguments.of("apiVersionNotReemitted", """
                        apiVersion: v1
                        application: x
                        capabilities: []
                        """, "v1"),
                Arguments.of("unknownFieldNotReemitted", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider:
                              id: acme
                              version: 1.2.3
                            foo: 1
                        """, "foo"));
    }
}
