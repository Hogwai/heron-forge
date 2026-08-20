package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory whose kind does not match the declared capability
 * type in the configuration.
 */
public final class WrongKindProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("wrong-kind", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("wrong-kind-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
    }
}