package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory with a mismatched provider version.
 */
public final class WrongVersionProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("wrong-version", "2.0.0", SpiMajor.V1, "out",
                TestProviders.schema("wrong-version-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
    }
}