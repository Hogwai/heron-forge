package dev.hogwai.platform.runtime.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Reflection-based API contract tests for the Task 6B provider surface.
 *
 * <p>Proves that the runtime registry exposes only the no-arg public
 * constructor (no public dynamic {@link ClassLoader} injection) and that the
 * public resolution result exposes only the factory and descriptor (no raw
 * configuration).
 */
class ProviderApiTest {

    @Test
    void registryExposesOnlyNoArgPublicConstructor() {
        Constructor<?>[] constructors = ServiceLoaderProviderRegistry.class.getConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterCount()).isZero();
        assertThat(Modifier.isPublic(constructors[0].getModifiers())).isTrue();
    }

    @Test
    void registryHasNoPublicClassLoaderConstructor() {
        boolean hasPublicClassLoaderConstructor = Arrays.stream(ServiceLoaderProviderRegistry.class.getConstructors())
                .anyMatch(c -> c.getParameterCount() == 1 && c.getParameterTypes()[0] == ClassLoader.class);

        assertThat(hasPublicClassLoaderConstructor).isFalse();
    }

    @Test
    void resolvedProviderExposesFactoryDescriptorAndDiagnostics() {
        RecordComponent[] components = ProviderResolver.ResolvedProvider.class.getRecordComponents();

        assertThat(components).extracting(RecordComponent::getName)
                .containsExactly("factory", "descriptor", "diagnostics");
    }

    @Test
    void resolvedProviderExposesNoRawConfig() {
        RecordComponent[] components = ProviderResolver.ResolvedProvider.class.getRecordComponents();

        assertThat(components).extracting(RecordComponent::getName)
                .isNotEmpty()
                .doesNotContain("rawConfig");
    }
}