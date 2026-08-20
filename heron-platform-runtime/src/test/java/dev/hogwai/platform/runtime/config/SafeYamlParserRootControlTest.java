package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.config.yaml.YamlLimits;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SafeYamlParserRootControlTest {

    private static final SafeYamlParser PARSER = new SafeYamlParser();

    private static ParsedApplication parse(String yaml) {
        return PARSER.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), YamlLimits.defaults());
    }

    @Test
    void rejectsRootAnchorAndAlias() {
        // A tag/anchor/alias on the root node must not bypass the policy.
        ParsedApplication anchoredRoot = parse("&root\napiVersion: platform.dev/v1alpha1\nkind: Application\n");
        ParsedApplication taggedRoot = parse("!custom\napiVersion: platform.dev/v1alpha1\nkind: Application\n");

        assertThat(anchoredRoot.isValid()).isFalse();
        assertThat(taggedRoot.isValid()).isFalse();
        assertThat(anchoredRoot.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(taggedRoot.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }
}
