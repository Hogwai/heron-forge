package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory for the {@code late-orders} transform capability.
 */
public final class LateOrdersProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.transform("late-orders", "1.0.0", SpiMajor.V1, "in",
                TestProviders.schema("late-orders-in", "id", new FieldType.StringType()),
                "out", TestProviders.schema("late-orders-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
    }
}