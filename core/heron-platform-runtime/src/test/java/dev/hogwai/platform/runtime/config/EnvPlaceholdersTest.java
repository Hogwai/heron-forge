package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.hogwai.platform.spi.Diagnostic;

/** Verifies environment placeholder resolution in configuration values. */
class EnvPlaceholdersTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void resolvesNestedPlaceholders() throws Exception {
        JsonNode root = root("""
                providers:
                  - id: demo.orders
                    config:
                      url: jdbc:postgresql://localhost:5432/heron_demo
                      password: ${HERON_DB_PASSWORD}
                """);
        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonNode resolved = EnvPlaceholders.resolve(root, diagnostics, Map.of("HERON_DB_PASSWORD", "s3cret")::get);

        assertThat(diagnostics).isEmpty();
        assertThat(resolved.at("/providers/0/config/password").asText()).isEqualTo("s3cret");
        assertThat(resolved.at("/providers/0/config/url").asText())
                .isEqualTo("jdbc:postgresql://localhost:5432/heron_demo");
    }

    @Test
    void reportsMissingVariable() throws Exception {
        JsonNode root = root("password: ${MISSING_SECRET}\n");
        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonNode resolved = EnvPlaceholders.resolve(root, diagnostics, name -> null);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.message()).contains("environment variable 'MISSING_SECRET' is not set");
            assertThat(diagnostic.path()).isEqualTo("/password");
        });
        assertThat(resolved.at("/password").asText()).isEqualTo("${MISSING_SECRET}");
    }

    @Test
    void reportsBlankVariableAsMissing() throws Exception {
        JsonNode root = root("password: ${EMPTY_SECRET}\n");
        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonNode resolved = EnvPlaceholders.resolve(root, diagnostics, name -> "  ");

        assertThat(diagnostics).singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message()).contains("'EMPTY_SECRET' is not set"));
        assertThat(resolved.at("/password").asText()).isEqualTo("${EMPTY_SECRET}");
    }

    @Test
    void rejectsPartialInterpolation() throws Exception {
        JsonNode root = root("password: prefix-${SECRET}\n");
        List<Diagnostic> diagnostics = new ArrayList<>();
        EnvPlaceholders.resolve(root, diagnostics, name -> "value");

        assertThat(diagnostics).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message()).contains("placeholder must span the entire value"));
    }

    @Test
    void rejectsMalformedReference() throws Exception {
        JsonNode root = root("password: \"${1BAD}\"\n");
        List<Diagnostic> diagnostics = new ArrayList<>();
        EnvPlaceholders.resolve(root, diagnostics, name -> "value");

        assertThat(diagnostics).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message()).contains("placeholder must span the entire value"));
    }

    @Test
    void leavesNonStringValuesUntouched() throws Exception {
        JsonNode root = root("""
                limits:
                  maxRows: 100
                  enabled: true
                  ratio: 0.5
                  empty: null
                """);
        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonNode resolved = EnvPlaceholders.resolve(root, diagnostics, name -> "value");

        assertThat(diagnostics).isEmpty();
        assertThat(resolved.at("/limits/maxRows").asLong()).isEqualTo(100);
        assertThat(resolved.at("/limits/enabled").asBoolean()).isTrue();
        assertThat(resolved.at("/limits/ratio").decimalValue().toString()).isEqualTo("0.5");
        assertThat(resolved.at("/limits/empty").isNull()).isTrue();
    }

    @Test
    void collectsOneDiagnosticPerFailure() throws Exception {
        JsonNode root = root("""
                first: ${A_MISSING}
                second: ${B_MISSING}
                """);
        List<Diagnostic> diagnostics = new ArrayList<>();
        EnvPlaceholders.resolve(root, diagnostics, name -> null);

        assertThat(diagnostics).hasSize(2);
    }

    private static JsonNode root(String yaml) throws Exception {
        return YAML.readTree(yaml);
    }
}
