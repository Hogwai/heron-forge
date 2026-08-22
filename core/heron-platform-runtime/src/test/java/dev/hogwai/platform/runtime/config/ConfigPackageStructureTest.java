package dev.hogwai.platform.runtime.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural test that locks in the reduced public surface of the runtime
 * config packages: implementation helpers must stay package-private, only the
 * cohesive gates and models are public.
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
    void yamlLimitsAndParserArePublic() throws Exception {
        assertThat(isPublicClass(BASE + ".YamlLimits")).isTrue();
        assertThat(isPublicClass(BASE + ".SafeYamlParser")).isTrue();
    }

    @Test
    void cohesiveGatesArePublic() throws Exception {
        assertThat(isPublicClass(BASE + ".mapping.ConfigMapper")).isTrue();
        assertThat(isPublicClass(BASE + ".safe.SafeConfig")).isTrue();
        assertThat(isPublicClass(BASE + ".Diagnostics")).isTrue();
    }

    @Test
    void configMapperExposesOnlyCompleteMappingOperation() throws Exception {
        List<String> publicMethods = Arrays.stream(Class.forName(BASE + ".mapping.ConfigMapper").getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .toList();
        assertThat(publicMethods).containsExactly("mapApplication");
    }
}
