package dev.hogwai.platform.runtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the encapsulated boundary of the load package. */
class SnapshotPackageStructureTest {

    @Test
    void exposesOnlyApplicationLoaderAtTheLoadBoundary() {
        assertThat(Modifier.isPublic(ApplicationLoader.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(RuntimeApplication.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(RuntimeEntrypoint.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(SnapshotBuilder.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(SnapshotCandidate.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(RuntimeSnapshot.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(ResourceTracker.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(PullInvoker.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(StructuredPayloadProjector.class.getModifiers())).isFalse();
    }

    @Test
    void exposesOnlyThePublicApplicationLoaderMethod() {
        List<Method> publicMethods = Arrays.stream(ApplicationLoader.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        assertThat(publicMethods).singleElement().satisfies(method -> {
            assertThat(method.getName()).isEqualTo("load");
            assertThat(method.getParameterTypes()).containsExactly(InputStream.class);
        });
    }

    @Test
    void keepsRuntimeSnapshotMethodsPackagePrivate() throws Exception {
        assertThat(Modifier.isPublic(RuntimeSnapshot.class.getDeclaredMethod("generationId").getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(RuntimeSnapshot.class.getDeclaredMethod("graph").getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(RuntimeSnapshot.class.getDeclaredMethod("instances").getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(RuntimeSnapshot.class.getDeclaredMethod("instance", String.class).getModifiers()))
                .isFalse();
    }

    @Test
    void keepsRuntimeEntrypointAccessorsPackagePrivate() throws Exception {
        assertThat(Modifier.isPublic(RuntimeEntrypoint.class.getDeclaredConstructor(
                dev.hogwai.platform.host.api.EntrypointDescriptor.class, String.class).getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(RuntimeEntrypoint.class.getDeclaredMethod("descriptor").getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(RuntimeEntrypoint.class.getDeclaredMethod("target").getModifiers()))
                .isFalse();
    }
}
