package dev.hogwai.platform.examples.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.provider.ProviderFactory;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class ProviderServiceLoaderTest {

    @Test
    void discoversAllFeatureFactories() {
        assertThat(ServiceLoader.load(ProviderFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(factory -> factory.descriptor().providerId().value()))
                .containsExactlyInAnyOrder("demo.orders", "demo.deliveries", "supply-chain.exception-detector");
    }
}
