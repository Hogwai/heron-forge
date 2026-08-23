package dev.hogwai.platform.registry;

import java.util.ServiceLoader;

import dev.hogwai.platform.spi.registry.GenerationStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code heron-platform-processor} generated
 * {@code META-INF/services} descriptor lets the {@code ServiceLoader}
 * discover {@link FileGenerationStore} as a {@link GenerationStore}.
 */
class GenerationStoreServiceLoaderTest {

    @Test
    void discoversFileGenerationStoreThroughServiceLoader() {
        assertThat(ServiceLoader.load(GenerationStore.class).stream())
                .singleElement()
                .satisfies(provider -> {
                    assertThat(provider.type()).isEqualTo(FileGenerationStore.class);
                    assertThat(provider.get()).isInstanceOf(FileGenerationStore.class);
                });
    }
}
