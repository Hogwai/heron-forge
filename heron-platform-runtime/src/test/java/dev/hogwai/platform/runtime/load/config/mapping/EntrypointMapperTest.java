package dev.hogwai.platform.runtime.load.config.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.hogwai.platform.runtime.load.config.entrypoint.EntrypointConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EntrypointMapperTest {

    private static final YAMLMapper MAPPER = new YAMLMapper();
    private static final String ENTRYPOINTS_PATH = "/spec/entrypoints";

    private static List<EntrypointConfig> map(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<EntrypointConfig> result = EntrypointMapper.mapEntrypoints(
                root.get("entrypoints"), ENTRYPOINTS_PATH, diagnostics);
        assertThat(diagnostics).isEmpty();
        return result;
    }

    private static List<Diagnostic> diagnostics(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        List<Diagnostic> diagnostics = new ArrayList<>();
        EntrypointMapper.mapEntrypoints(root.get("entrypoints"), ENTRYPOINTS_PATH, diagnostics);
        return diagnostics;
    }

    @Test
    void mapsValidEntrypoint() throws Exception {
        assertThat(map("""
                entrypoints:
                  - id: exceptions
                    method: GET
                    path: /exceptions
                    target: detector
                """))
                .containsExactly(new EntrypointConfig("exceptions", "GET", "/exceptions", "detector"));
    }

    @Test
    void absentEntrypointsDefaultToEmpty() throws Exception {
        assertThat(map("other: value\n")).isEmpty();
    }

    @Test
    void rejectsUnknownEntrypointFields() throws Exception {
        assertThat(diagnostics("""
                entrypoints:
                  - id: exceptions
                    method: GET
                    path: /exceptions
                    target: detector
                    extra: secret
                """))
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR,
                        ENTRYPOINTS_PATH + "/0/<key>"));
    }

    @ParameterizedTest
    @MethodSource("missingFieldCases")
    void rejectsMissingRequiredFieldsAtTheirFieldPaths(String field, String yaml) throws Exception {
        assertThat(diagnostics(yaml)).extracting(Diagnostic::path)
                .contains(ENTRYPOINTS_PATH + "/0/" + field);
    }

    private static Stream<Arguments> missingFieldCases() {
        return Stream.of(
                Arguments.of("id", """
                        entrypoints:
                          - method: GET
                            path: /exceptions
                            target: detector
                        """),
                Arguments.of("method", """
                        entrypoints:
                          - id: exceptions
                            path: /exceptions
                            target: detector
                        """),
                Arguments.of("path", """
                        entrypoints:
                          - id: exceptions
                            method: GET
                            target: detector
                        """),
                Arguments.of("target", """
                        entrypoints:
                          - id: exceptions
                            method: GET
                            path: /exceptions
                        """));
    }

    @ParameterizedTest
    @MethodSource("blankFieldCases")
    void rejectsBlankRequiredFieldsAtTheirFieldPaths(String field, String yaml) throws Exception {
        assertThat(diagnostics(yaml))
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR,
                        ENTRYPOINTS_PATH + "/0/" + field));
    }

    private static Stream<Arguments> blankFieldCases() {
        return Stream.of(
                Arguments.of("id", """
                        entrypoints:
                          - id: '  '
                            method: GET
                            path: /exceptions
                            target: detector
                        """),
                Arguments.of("method", """
                        entrypoints:
                          - id: exceptions
                            method: '  '
                            path: /exceptions
                            target: detector
                        """),
                Arguments.of("path", """
                        entrypoints:
                          - id: exceptions
                            method: GET
                            path: '  '
                            target: detector
                        """));
    }

    @Test
    void rejectsDuplicateIdsAndPaths() throws Exception {
        List<Diagnostic> result = diagnostics("""
                entrypoints:
                  - id: duplicate
                    method: GET
                    path: /one
                    target: detector
                  - id: duplicate
                    method: GET
                    path: /one
                    target: detector
                """);

        assertThat(result).extracting(Diagnostic::path)
                .contains(ENTRYPOINTS_PATH + "/1/id", ENTRYPOINTS_PATH + "/1/path");
    }

    @Test
    void rejectsNonGetMethod() throws Exception {
        assertThat(diagnostics("""
                entrypoints:
                  - id: exceptions
                    method: POST
                    path: /exceptions
                    target: detector
                """))
                .extracting(Diagnostic::path)
                .containsExactly(ENTRYPOINTS_PATH + "/0/method");
    }

    @Test
    void rejectsInvalidPathForms() throws Exception {
        for (String path : List.of("exceptions", "/exceptions?format=json", "/exceptions#fragment")) {
            assertThat(diagnostics("""
                    entrypoints:
                      - id: exceptions
                        method: GET
                        path: %s
                        target: detector
                    """.formatted(path)))
                    .extracting(Diagnostic::path)
                    .contains(ENTRYPOINTS_PATH + "/0/path");
        }
    }

    @Test
    void rejectsBlankTarget() throws Exception {
        assertThat(diagnostics("""
                entrypoints:
                  - id: exceptions
                    method: GET
                    path: /exceptions
                    target: '  '
                """))
                .extracting(Diagnostic::path)
                .containsExactly(ENTRYPOINTS_PATH + "/0/target");
    }
}
