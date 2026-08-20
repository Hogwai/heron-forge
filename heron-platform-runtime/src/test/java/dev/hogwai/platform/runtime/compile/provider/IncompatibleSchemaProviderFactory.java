package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory whose input schema is incompatible with the
 * {@code orders} output schema.
 */
public final class IncompatibleSchemaProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.transform("incompatible-schema", "1.0.0", SpiMajor.V1, "in",
                TestProviders.schema("incompatible-in", "amount", new FieldType.DecimalType()),
                "out", TestProviders.schema("incompatible-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
    }
}