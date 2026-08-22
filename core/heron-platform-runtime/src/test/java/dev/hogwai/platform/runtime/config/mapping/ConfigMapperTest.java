package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ConfigMapperTest {

    private static final YAMLMapper MAPPER = new YAMLMapper();

    private static ParsedApplication validate(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        return ConfigMapper.mapApplication(root);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ConfigMapperTest.class.getResourceAsStream("/config/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void acceptsValidApplication() throws Exception {
        ParsedApplication result = validate(resource("valid-factory.yaml"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.application().name()).isEqualTo("factory-demo");
        assertThat(result.application().capabilities()).hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaErrorCases")
    void rejectsInvalidApplication(String caseName, String yaml, String expectedPath) throws Exception {
        ParsedApplication result = validate(yaml);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, expectedPath));
    }

    private static Stream<Arguments> schemaErrorCases() {
        return Stream.of(
                Arguments.of("rejectsUnknownRootField", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities: []
                        unknown: 1
                        """, "/<key>"),
                Arguments.of("rejectsLegacyRootFields", """
                        apiVersion: platform.dev/v1alpha1
                        kind: Application
                        metadata:
                          name: x
                        spec:
                          capabilities: []
                        """, "/<key>"),
                Arguments.of("rejectsMissingApplication", """
                        apiVersion: heron.dev/v1
                        capabilities: []
                        """, "/application"),
                Arguments.of("rejectsBlankApplication", """
                        apiVersion: heron.dev/v1
                        application: '  '
                        capabilities: []
                        """, "/application"),
                Arguments.of("rejectsDuplicateCapabilityId", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider: {id: acme, version: 1.2.3}
                          - id: c
                            provider: {id: acme, version: 1.2.3}
                        """, "/capabilities/1/id"),
                Arguments.of("rejectsCapabilityType", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            type: source
                            provider: {id: acme, version: 1.2.3}
                        """, "/capabilities/0/<key>"),
                Arguments.of("rejectsMissingCapabilityId", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - provider: {id: acme, version: 1.2.3}
                        """, "/capabilities/0/id"),
                Arguments.of("rejectsMissingProviderVersion", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider: {id: acme}
                        """, "/capabilities/0/provider/version"),
                Arguments.of("rejectsNonCanonicalProviderVersion", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider: {id: acme, version: 1.2}
                        """, "/capabilities/0/provider/version"),
                Arguments.of("rejectsInvalidInputs", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider: {id: acme, version: 1.2.3}
                            inputs:
                              in: {capability: a, port: out, extra: 1}
                        """, "/capabilities/0/inputs/<key>/<key>"),
                Arguments.of("rejectsMissingInputPort", """
                        apiVersion: heron.dev/v1
                        application: x
                        capabilities:
                          - id: c
                            provider: {id: acme, version: 1.2.3}
                            inputs:
                              in: {capability: a}
                        """, "/capabilities/0/inputs/<key>/port"),
                Arguments.of("rejectsWrongApiVersion", """
                        apiVersion: platform.dev/v1alpha1
                        application: x
                        capabilities: []
                        """, "/apiVersion"));
    }

    @Test
    void rejectsUnknownCapabilityField() throws Exception {
        ParsedApplication result = validate(resource("invalid-unknown-field.yaml"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/capabilities/0/<key>"));
    }

    @Test
    void rejectsUnknownEndpointFieldAndDuplicateIdsAndPaths() throws Exception {
        ParsedApplication result = validate("""
                apiVersion: heron.dev/v1
                application: x
                capabilities: []
                endpoints:
                  - id: read
                    method: GET
                    path: /read
                    target: missing
                    extra: 1
                  - id: read
                    method: GET
                    path: /read
                    target: missing
                """);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics()).extracting(Diagnostic::path)
                .contains("/endpoints/0/<key>", "/endpoints/1/id", "/endpoints/1/path");
    }

    @Test
    void rejectsInvalidEndpointMethodAndPath() throws Exception {
        ParsedApplication result = validate("""
                apiVersion: heron.dev/v1
                application: x
                capabilities: []
                endpoints:
                  - id: read
                    method: POST
                    path: relative?query
                    target: missing
                """);

        assertThat(result.diagnostics()).extracting(Diagnostic::path)
                .contains("/endpoints/0/method");
    }
}
