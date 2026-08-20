package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory for the {@code orders} source capability.
 */
public final class OrdersProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("orders", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("orders-out", "id", new FieldType.StringType()),
                TestProviders.hostConfigSchema());
    }
}