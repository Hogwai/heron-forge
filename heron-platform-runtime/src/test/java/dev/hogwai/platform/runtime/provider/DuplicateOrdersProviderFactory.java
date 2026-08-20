package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory that duplicates the {@code orders} provider id.
 */
public final class DuplicateOrdersProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("orders", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("orders-out", "id", new FieldType.StringType()),
                TestProviders.hostConfigSchema());
    }
}