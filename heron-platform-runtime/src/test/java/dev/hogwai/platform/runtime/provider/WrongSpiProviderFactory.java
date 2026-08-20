package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory with an unsupported SPI major version.
 */
public final class WrongSpiProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("wrong-spi", "1.0.0", 2, "out",
                TestProviders.schema("wrong-spi-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
    }
}