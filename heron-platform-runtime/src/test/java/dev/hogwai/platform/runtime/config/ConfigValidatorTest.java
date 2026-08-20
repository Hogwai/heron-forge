package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConfigValidatorTest {

    private static final ConfigValidator VALIDATOR = new ConfigValidator();
    private static final YAMLMapper MAPPER = new YAMLMapper();

    private static ParsedApplication validate(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        return VALIDATOR.validate(root);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ConfigValidatorTest.class.getResourceAsStream("/config/" + name)) {
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
                        apiVersion: platform.dev/v1alpha1
                        kind: Application
                        metadata:
                          name: x
                        spec:
                          capabilities: []
                        unknown: 1
                        """, "/<key>"),
                Arguments.of("rejectsMissingMetadata", """
                        apiVersion: platform.dev/v1alpha1
                        kind: Application
                        spec:
                          capabilities: []
                        """, "/metadata"),
                Arguments.of("rejectsBlankName", """
                        apiVersion: platform.dev/v1alpha1
                        kind: Application
                        metadata:
                          name: '  '
                        spec:
                          capabilities: []
                        """, "/metadata/name"),
                Arguments.of("rejectsDuplicateCapabilityId", """
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
                            - id: c
                              type: transform
                              provider:
                                id: acme
                                version: 1.2.3
                        """, "/spec/capabilities/1/id"),
                Arguments.of("rejectsUnsupportedType", """
                        apiVersion: platform.dev/v1alpha1
                        kind: Application
                        metadata:
                          name: x
                        spec:
                          capabilities:
                            - id: c
                              type: sink
                              provider:
                                id: acme
                                version: 1.2.3
                        """, "/spec/capabilities/0/type"),
                Arguments.of("rejectsMissingRequiredCapabilityMember", """
                        apiVersion: platform.dev/v1alpha1
                        kind: Application
                        metadata:
                          name: x
                        spec:
                          capabilities:
                            - type: source
                              provider:
                                id: acme
                                version: 1.2.3
                        """, "/spec/capabilities/0/id"));
    }

    @Test
    void rejectsWrongRootIdentity() throws Exception {
        ParsedApplication wrongVersion = validate("""
                apiVersion: v1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities: []
                """);
        ParsedApplication wrongKind = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Deployment
                metadata:
                  name: x
                spec:
                  capabilities: []
                """);

        assertThat(wrongVersion.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/apiVersion"));
        assertThat(wrongKind.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/kind"));
    }

    @Test
    void rejectsUnknownCapabilityField() throws Exception {
        ParsedApplication result = validate(resource("invalid-unknown-field.yaml"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/<key>"));
    }

    @Test
    void rejectsBadProviderVersion() throws Exception {
        ParsedApplication missing = validate("""
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
                """);
        ParsedApplication invalid = validate("""
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
                        version: 1.2
                """);

        assertThat(missing.isValid()).isFalse();
        assertThat(invalid.isValid()).isFalse();
        assertThat(missing.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/provider/version"));
        assertThat(invalid.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/provider/version"));
    }

    @Test
    void rejectsInvalidInputBinding() throws Exception {
        ParsedApplication unknownField = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities:
                    - id: c
                      type: transform
                      provider:
                        id: acme
                        version: 1.2.3
                      inputs:
                        in:
                          capability: a
                          port: out
                          extra: 1
                """);
        ParsedApplication missingPort = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities:
                    - id: c
                      type: transform
                      provider:
                        id: acme
                        version: 1.2.3
                      inputs:
                        in:
                          capability: a
                """);

        assertThat(unknownField.isValid()).isFalse();
        assertThat(missingPort.isValid()).isFalse();
        assertThat(unknownField.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR,
                        "/spec/capabilities/0/inputs/<key>/<key>"));
        assertThat(missingPort.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR,
                        "/spec/capabilities/0/inputs/<key>/port"));
    }

    @Test
    void rejectsListFormInputs() throws Exception {
        ParsedApplication result = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities:
                    - id: c
                      type: transform
                      provider:
                        id: acme
                        version: 1.2.3
                      inputs:
                        - capability: a
                          port: out
                """);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/inputs"));
    }

    @Test
    void rejectsNonObjectSections() throws Exception {
        ParsedApplication badMetadata = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata: nope
                spec:
                  capabilities: []
                """);
        ParsedApplication badSpec = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec: nope
                """);
        ParsedApplication badCapabilities = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities: nope
                """);
        ParsedApplication badConfig = validate("""
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
                      config: nope
                """);
        ParsedApplication badProvider = validate("""
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities:
                    - id: c
                      type: source
                      provider: nope
                """);

        assertThat(badMetadata.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/metadata"));
        assertThat(badSpec.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec"));
        assertThat(badCapabilities.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities"));
        assertThat(badConfig.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/config"));
        assertThat(badProvider.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/provider"));
    }
}