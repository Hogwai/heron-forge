package dev.hogwai.platform.runtime.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

/**
 * Fixture provider factory for the registry tests: a source capability that
 * records how many instances were created and exposes the last
 * {@link ExecutionContext} observed during execution.
 */
final class RegistryTestSourceFactory implements ProviderFactory {

    private final ProviderDescriptor descriptor = new ProviderDescriptor(
            new ProviderId("orders"), ProviderVersion.parse("1.0.0"), CapabilityKind.SOURCE, SpiMajor.V1,
            Map.of(),
            Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), schema(), true)),
            new ConfigurationSchema(Set.of("host"), Set.of("host"),
                    Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of()));
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicReference<ExecutionContext> lastContext = new AtomicReference<>();

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public List<dev.hogwai.platform.spi.Diagnostic> validate(Map<String, Object> rawConfig) {
        return List.of();
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        creations.incrementAndGet();
        return new Instance(this);
    }

    int creations() {
        return creations.get();
    }

    ExecutionContext lastContext() {
        return lastContext.get();
    }

    private static Schema schema() {
        return new Schema("registry-out", 1,
                List.of(new Field(new FieldId("id"), "id", new FieldType.StringType(), false, Optional.empty())),
                false);
    }

    private MaterializedDataSet dataset() {
        return new MaterializedDataSet(schema(), List.of(),
                new DataSetMetadata("registry-source", new DataSetLimits(100, 1000)), 0);
    }

    /**
     * Capability instance that captures the execution context before returning
     * an empty materialized dataset.
     */
    private record Instance(RegistryTestSourceFactory owner) implements CapabilityInstance {

        @Override
        public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
            owner.lastContext.set(context);
            return owner.dataset();
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
