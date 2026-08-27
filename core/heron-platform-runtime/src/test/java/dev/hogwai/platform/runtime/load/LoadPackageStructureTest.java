package dev.hogwai.platform.runtime.load;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the public facade boundary of the load package.
 */
class LoadPackageStructureTest {

    @Test
    void exposesOnlyTheFacadeAtTheLoadBoundary() {
        assertThat(Modifier.isPublic(ApplicationLoader.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(ValidationReport.class.getModifiers())).isTrue();
    }

    @Test
    void exposesOnlyThePublicApplicationLoaderMethods() {
        List<Method> publicMethods = Arrays.stream(ApplicationLoader.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        assertThat(publicMethods).hasSize(2)
                .allSatisfy(method -> assertThat(method.getParameterTypes()).containsExactly(InputStream.class));
        assertThat(publicMethods).extracting(Method::getName)
                .containsExactlyInAnyOrder("load", "validate");
    }
}