package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import org.junit.jupiter.api.Test;

class ConfigValidatorJacksonNodeTest {

    private static final ConfigValidator VALIDATOR = new ConfigValidator();

    @Test
    void rejectsUnexpectedJacksonConfigNode() {
        // A config value that is not one of the supported v1 node types must be
        // explicitly refused rather than silently converted via asText().
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.set("binary", JsonNodeFactory.instance.binaryNode(new byte[] {1, 2, 3}));

        ObjectNode capability = JsonNodeFactory.instance.objectNode();
        capability.put("id", "c");
        capability.put("type", "source");
        ObjectNode provider = JsonNodeFactory.instance.objectNode();
        provider.put("id", "acme");
        provider.put("version", "1.2.3");
        capability.set("provider", provider);
        capability.set("config", config);

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("apiVersion", "platform.dev/v1alpha1");
        root.put("kind", "Application");
        ObjectNode metadata = JsonNodeFactory.instance.objectNode();
        metadata.put("name", "x");
        root.set("metadata", metadata);
        ObjectNode spec = JsonNodeFactory.instance.objectNode();
        spec.putArray("capabilities").add(capability);
        root.set("spec", spec);

        ParsedApplication result = VALIDATOR.validate(root);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/spec/capabilities/0/config"));
    }
}
