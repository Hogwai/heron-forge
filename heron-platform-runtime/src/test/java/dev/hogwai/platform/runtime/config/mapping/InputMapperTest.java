package dev.hogwai.platform.runtime.config.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.hogwai.platform.runtime.config.input.InputBindingConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@code inputs} mapping form: each key is the target
 * input port name and each value is a binding with exactly {@code capability}
 * and {@code port}.
 */
class InputMapperTest {

    private static final YAMLMapper MAPPER = new YAMLMapper();
    private static final String INPUTS_PATH = "/spec/capabilities/0/inputs";

    private static List<InputBindingConfig> mapInputs(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<InputBindingConfig> result = InputMapper.mapInputs(root.get("inputs"), INPUTS_PATH, diagnostics);
        assertThat(diagnostics).isEmpty();
        return result;
    }

    private static List<Diagnostic> mapInputsDiagnostics(String yaml) throws IOException {
        JsonNode root = MAPPER.readTree(yaml);
        List<Diagnostic> diagnostics = new ArrayList<>();
        InputMapper.mapInputs(root.get("inputs"), INPUTS_PATH, diagnostics);
        return diagnostics;
    }

    @Test
    void preservesTargetInputPortName() throws Exception {
        List<InputBindingConfig> result = mapInputs("""
                inputs:
                  orders:
                    capability: orders
                    port: records
                """);

        assertThat(result).containsExactly(new InputBindingConfig("orders", "orders", "records"));
        assertThat(result.get(0).inputPort()).isEqualTo("orders");
        assertThat(result.get(0).capability()).isEqualTo("orders");
        assertThat(result.get(0).port()).isEqualTo("records");
    }

    @Test
    void preservesMultipleDistinctPortsInOrder() throws Exception {
        List<InputBindingConfig> result = mapInputs("""
                inputs:
                  first:
                    capability: a
                    port: out1
                  second:
                    capability: b
                    port: out2
                  third:
                    capability: c
                    port: out3
                """);

        assertThat(result).containsExactly(
                new InputBindingConfig("first", "a", "out1"),
                new InputBindingConfig("second", "b", "out2"),
                new InputBindingConfig("third", "c", "out3"));
    }

    @Test
    void rejectsListForm() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("""
                inputs:
                  - capability: a
                    port: out
                """);

        assertThat(diagnostics)
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, INPUTS_PATH));
    }

    @Test
    void rejectsScalarInputs() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("inputs: nope\n");

        assertThat(diagnostics)
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, INPUTS_PATH));
    }

    @Test
    void rejectsBindingWithUnknownField() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("""
                inputs:
                  in:
                    capability: a
                    port: out
                    extra: 1
                """);

        assertThat(diagnostics)
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, INPUTS_PATH + "/<key>/<key>"));
    }

    @Test
    void rejectsBindingWithMissingRequiredField() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("""
                inputs:
                  in:
                    capability: a
                """);

        assertThat(diagnostics)
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, INPUTS_PATH + "/<key>/port"));
    }

    @Test
    void rejectsBlankInputPortKey() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("""
                inputs:
                  '  ':
                    capability: a
                    port: out
                """);

        assertThat(diagnostics)
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, INPUTS_PATH + "/<key>"));
    }

    @Test
    void rejectsBindingThatIsNotAMapping() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("""
                inputs:
                  in: nope
                """);

        assertThat(diagnostics)
                .extracting(Diagnostic::code, Diagnostic::path)
                .containsExactly(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, INPUTS_PATH + "/<key>"));
    }

    @Test
    void doesNotLeakUserKeysIntoPathsOrMessages() throws Exception {
        List<Diagnostic> diagnostics = mapInputsDiagnostics("""
                inputs:
                  secret-key-xyz:
                    capability: a
                    port: out
                    secret-field-xyz: 1
                """);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics)
                .extracting(Diagnostic::path)
                .noneMatch(path -> path.contains("secret-key-xyz") || path.contains("secret-field-xyz"));
        assertThat(diagnostics)
                .extracting(Diagnostic::message)
                .noneMatch(message -> message.contains("secret-key-xyz") || message.contains("secret-field-xyz"));
        assertThat(diagnostics)
                .extracting(Diagnostic::path)
                .allMatch(path -> path.contains("<key>"));
    }
}