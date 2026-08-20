package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.runtime.load.config.yaml.YamlLimits;
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

    @Test
    void rejectsExplicitTagAndNonMappingRoot() {
        ParsedApplication tagged = parse("!!str platform.dev/v1alpha1\n");
        ParsedApplication taggedInteger = parse("!!int 1\n");
        ParsedApplication customTagged = parse("!custom value\n");
        ParsedApplication sequence = parse("- value\n");
        ParsedApplication emptyDocument = parse("---\n");
        ParsedApplication emptyRoot = parse("");
        ParsedApplication tagDirective = parse("%TAG !e! tag:example.com,2026:\n---\n!e!value x\n");

        assertThat(tagged.isValid()).isFalse();
        assertThat(taggedInteger.isValid()).isFalse();
        assertThat(customTagged.isValid()).isFalse();
        assertThat(tagged.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(taggedInteger.diagnostics())
                .extracting(Diagnostic::message)
                .contains("explicit YAML tag is not allowed");
        assertThat(customTagged.diagnostics())
                .extracting(Diagnostic::message)
                .contains("explicit YAML tag is not allowed");
        assertThat(sequence.isValid()).isFalse();
        assertThat(sequence.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(emptyDocument.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(emptyRoot.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
        assertThat(tagDirective.diagnostics())
                .extracting(Diagnostic::code)
                .contains(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    @Test
    void rejectsAliasWithoutAnchorInSameDocument() {
        ParsedApplication result = parse("*missing\n");

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::message)
                .contains(org.assertj.core.groups.Tuple.tuple(PlatformErrorCode.CONFIG_PARSE_ERROR,
                        "YAML aliases are not allowed"));
    }
}
