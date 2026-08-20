package dev.hogwai.platform.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.hogwai.platform.runtime.config.yaml.YamlDocument;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Structural test that locks in the reduced public surface of the runtime
 * config packages: implementation helpers must stay package-private, only the
 * cohesive gates and models are public, and the YAML parse result is immutable.
 */
class ConfigPackageStructureTest {

    private static final String BASE = "dev.hogwai.platform.runtime.config";

    private static boolean isPublicClass(String name) throws ClassNotFoundException {
        return Modifier.isPublic(Class.forName(name).getModifiers());
    }

    @Test
    void mappingHelpersAreNotPublic() throws Exception {
        assertThat(isPublicClass(BASE + ".mapping.FieldChecks")).isFalse();
        assertThat(isPublicClass(BASE + ".mapping.ProviderMapper")).isFalse();
        assertThat(isPublicClass(BASE + ".mapping.ProviderRef")).isFalse();
        assertThat(isPublicClass(BASE + ".mapping.CapabilityMapper")).isFalse();
        assertThat(isPublicClass(BASE + ".mapping.InputMapper")).isFalse();
    }

    @Test
    void safeHelpersAreNotPublic() throws Exception {
        assertThat(isPublicClass(BASE + ".safe.SafeValues")).isFalse();
        assertThat(isPublicClass(BASE + ".safe.SafeConfigValue")).isFalse();
        assertThat(isPublicClass(BASE + ".safe.SafeScalars")).isFalse();
    }

    @Test
    void yamlHelpersAreNotPublic() throws Exception {
        assertThat(isPublicClass(BASE + ".yaml.YamlTreeBuilder")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.YamlObjectBuilder")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.YamlValueReader")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.YamlKeyCheck")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.YamlStringReader")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.YamlNumberReader")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.YamlForbiddenCheck")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.ParseState")).isFalse();
        assertThat(isPublicClass(BASE + ".yaml.ForbiddenContent")).isFalse();
    }

    @Test
    void cohesiveGatesArePublic() throws Exception {
        assertThat(isPublicClass(BASE + ".mapping.ConfigMapper")).isTrue();
        assertThat(isPublicClass(BASE + ".safe.SafeConfig")).isTrue();
        assertThat(isPublicClass(BASE + ".yaml.YamlDocumentParser")).isTrue();
        assertThat(isPublicClass(BASE + ".diagnostics.Diagnostics")).isTrue();
    }

    @Test
    void configMapperExposesOnlyCompleteMappingOperation() throws Exception {
        List<String> publicMethods = Arrays.stream(Class.forName(BASE + ".mapping.ConfigMapper").getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .toList();
        assertThat(publicMethods).containsExactly("mapApplication");
    }

    @Test
    void yamlDocumentResultIsImmutable() {
        Diagnostic error = new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR,
                "/kind", "unsupported kind", "use 'Application'");
        JsonNode root = JsonNodeFactory.instance.objectNode();
        YamlDocument document = new YamlDocument(root, List.of(error));

        assertThat(document.isValid()).isFalse();
        assertThat(document.root()).isSameAs(root);
        assertThat(document.diagnostics()).containsExactly(error);
        List<Diagnostic> diagnostics = document.diagnostics();
        assertThatThrownBy(() -> diagnostics.add(error))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
