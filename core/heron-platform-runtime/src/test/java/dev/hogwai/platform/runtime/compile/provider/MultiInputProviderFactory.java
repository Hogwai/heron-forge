package dev.hogwai.platform.runtime.compile.provider;

import java.util.Map;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory for a transform with two required input ports.
 */
public final class MultiInputProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(
                new ProviderId("multi-input"), ProviderVersion.parse("1.0.0"),
                CapabilityKind.TRANSFORM, SpiMajor.V1,
                Map.of(new PortId("left"), new PortDescriptor(new PortId("left"),
                                TestProviders.schema("multi-left", "id", new FieldType.StringType()), true),
                        new PortId("right"), new PortDescriptor(new PortId("right"),
                                TestProviders.schema("multi-right", "id", new FieldType.StringType()), true)),
                Map.of(new PortId("out"), new PortDescriptor(new PortId("out"),
                        TestProviders.schema("multi-out", "id", new FieldType.StringType()), true)),
                TestProviders.emptyConfigSchema());
    }
}
