package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.load.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the former multicloud secret-shaped lexical detection has been
 * removed: secret-shaped values and keys are now treated as plain scalar
 * content and no longer produce false-positive rejections.
 */
class SafeYamlParserSecretTest {

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
    void awsAccessKeyValueIsAccepted() {
        ParsedApplication result = parse(capabilityWithConfig("        key: AKIAIOSFODNN7EXAMPLE\n"));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void githubPatValueIsAccepted() {
        String secret = "github_pat_1234567890_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        ParsedApplication result = parse(capabilityWithConfig("        key: " + secret + "\n"));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void skProjValueIsAccepted() {
        String secret = "sk-proj-1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        ParsedApplication result = parse(capabilityWithConfig("        key: " + secret + "\n"));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void slackAndStripeValuesAreAccepted() {
        String slack = "xoxb-1234567890-123456789012-abcdefghijklmnopqrstuvwx";
        String stripe = "sk_live_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        assertThat(parse(capabilityWithConfig("        key: " + slack + "\n")).isValid()).isTrue();
        assertThat(parse(capabilityWithConfig("        key: " + stripe + "\n")).isValid()).isTrue();
    }

    @Test
    void secretShapedKeyIsAccepted() {
        String secretKey = "github_pat_1234567890_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        ParsedApplication result = parse(capabilityWithConfig("        " + secretKey + ": x\n"));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void diagnosticsNeverReemitUserValues() {
        // Even when a document is invalid, diagnostics must not copy raw user
        // values into messages or paths.
        ParsedApplication result = parse("""
                apiVersion: v1
                kind: Application
                metadata:
                  name: x
                spec:
                  capabilities: []
                """);

        assertThat(result.isValid()).isFalse();
        for (Diagnostic d : result.diagnostics()) {
            assertThat(d.message()).doesNotContain("v1");
        }
    }
}
