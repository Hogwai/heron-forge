package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.hogwai.platform.runtime.load.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.load.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeYamlParserTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static ParsedApplication parse(String yaml) {
        return parse(yaml, YamlLimits.defaults());
    }

    private static ParsedApplication parse(String yaml, YamlLimits limits) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), limits);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = SafeYamlParserTest.class.getResourceAsStream("/config/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesValidDocument() throws Exception {
        ParsedApplication result = parse(resource("valid-factory.yaml"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();

        ApplicationConfig app = result.application();
        assertThat(app.apiVersion()).isEqualTo("platform.dev/v1alpha1");
        assertThat(app.kind()).isEqualTo("Application");
        assertThat(app.name()).isEqualTo("factory-demo");
        assertThat(app.capabilities()).hasSize(2);

        CapabilityConfig orders = app.capabilities().get(0);
        assertThat(orders.id()).isEqualTo("orders");
        assertThat(orders.type()).isEqualTo(CapabilityKind.SOURCE);
        assertThat(orders.providerId()).isEqualTo("acme");
        assertThat(orders.providerVersion()).isEqualTo("1.2.3");
        assertThat(orders.config()).containsEntry("host", "localhost");
        assertThat(orders.config()).containsEntry("port", 8080L);
        assertThat(orders.config()).containsEntry("retries", 3L);
        assertThat(orders.config()).containsEntry("tags", List.of("a", "b"));
        assertThat(orders.inputs()).isEmpty();

        CapabilityConfig enrich = app.capabilities().get(1);
        assertThat(enrich.type()).isEqualTo(CapabilityKind.TRANSFORM);
        assertThat(enrich.inputs()).hasSize(1);
        assertThat(enrich.inputs().get(0).inputPort()).isEqualTo("in");
        assertThat(enrich.inputs().get(0).capability()).isEqualTo("orders");
        assertThat(enrich.inputs().get(0).port()).isEqualTo("out");
    }

    @Test
    void rejectsDuplicateKeys() throws Exception {
        ParsedApplication result = parse(resource("invalid-duplicate-key.yaml"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.application()).isNull();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_PARSE_ERROR, "/metadata/name"));
    }

    @Test
    void rejectsCustomTag() {
        ParsedApplication result = parse("apiVersion: !include platform.dev/v1alpha1\nkind: Application\n");

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_PARSE_ERROR, "/apiVersion"));
    }

    @Test
    void rejectsAnchorsAndAliases() {
        ParsedApplication alias = parse("""
                apiVersion: &v platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: *v
                """);
        ParsedApplication anchor = parse("metadata: &m\n  name: x\n");

        assertThat(alias.isValid()).isFalse();
        assertThat(anchor.isValid()).isFalse();
        assertThat(alias.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(anchor.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsOversizedDocument() {
        String big = "kind: " + "x".repeat(1000) + "\n";
        YamlLimits tiny = new YamlLimits(64, 20, 10_000, 4096);

        ParsedApplication result = parse(big, tiny);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_PARSE_ERROR, null));
    }

    @Test
    void rejectsExcessiveDepth() throws Exception {
        ParsedApplication result = parse(resource("invalid-limit.yaml"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsStructuralParseErrors() {
        ParsedApplication malformed = parse("kind: [unclosed\n");
        ParsedApplication multiDoc = parse("kind: Application\n---\nkind: Other\n");

        assertThat(malformed.isValid()).isFalse();
        assertThat(multiDoc.isValid()).isFalse();
        assertThat(malformed.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(multiDoc.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsEnvironmentInterpolation() {
        ParsedApplication result = parse("kind: ${APP_KIND}\n");

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void acceptsPlainScalarContentWithoutFalsePositives() {
        // include directives and secret-shaped values are no longer rejected at
        // parse time: they are treated as plain scalar content (no multicloud
        // detection). Any rejection here is a schema-level kind mismatch, not a
        // parse-level forbidden-content error.
        ParsedApplication include = parse("kind: include\n");
        ParsedApplication secret = parse("kind: AKIAIOSFODNN7EXAMPLE\n");

        assertThat(include.diagnostics())
                .extracting(Diagnostic::code)
                .isNotEmpty()
                .doesNotContain(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(secret.diagnostics())
                .extracting(Diagnostic::code)
                .isNotEmpty()
                .doesNotContain(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void parsesScalarValueTypes() {
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
                        ratio: 1.5
                        enabled: true
                        nothing: null
                        nested:
                          key: value
                """);

        assertThat(result.isValid()).isTrue();
        Map<String, Object> config = result.application().capabilities().getFirst().config();
        assertThat(config).containsEntry("ratio", new java.math.BigDecimal("1.5"))
                .containsEntry("enabled", true)
                .containsKey("nothing");
        assertThat(config.get("nothing")).isNull();
        assertThat(config).containsEntry("nested",Map.of("key", "value"));
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new YamlLimits(0, 20, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YamlLimits(100, 0, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YamlLimits(100, 20, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YamlLimits(100, 20, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
