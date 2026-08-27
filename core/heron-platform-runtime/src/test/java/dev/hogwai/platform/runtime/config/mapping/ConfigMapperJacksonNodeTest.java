package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ConfigMapperJacksonNodeTest {


    @Test
    void rejectsUnexpectedJacksonConfigNode() {
        // A config value that is not one of the supported v1 node types must be
        // explicitly refused rather than silently converted via asText().
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.set("binary", JsonNodeFactory.instance.binaryNode(new byte[]{1, 2, 3}));

        ObjectNode capability = JsonNodeFactory.instance.objectNode();
        capability.put("id", "c");
        ObjectNode provider = JsonNodeFactory.instance.objectNode();
        provider.put("id", "acme");
        provider.put("version", "1.2.3");
        capability.set("provider", provider);
        capability.set("config", config);

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("apiVersion", "heron.dev/v1");
        root.put("application", "x");
        root.putArray("capabilities").add(capability);

        ParsedApplication result = ConfigMapper.mapApplication(root);

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::path)
                .contains(tuple(PlatformErrorCode.CONFIG_SCHEMA_ERROR, "/capabilities/0/config"));
    }
}
