package dev.hogwai.platform.cli;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

/**
 * Minimal fixture provider registered through the test ServiceLoader file so
 * that registry validation and activation succeed during CLI tests.
 */
public final class CliTestOrdersProviderFactory implements ProviderFactory {

    private final ProviderDescriptor descriptor = new ProviderDescriptor(
            new ProviderId("cli-orders"), ProviderVersion.parse("1.0.0"), CapabilityKind.SOURCE, SpiMajor.V1,
            Map.of(),
            Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), schema(), true)),
            new ConfigurationSchema(Set.of("host"), Set.of("host"),
                    Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of()));

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return List.of();
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        return new EmptyInstance();
    }

    private static Schema schema() {
        return new Schema("cli-orders-out", 1,
                List.of(new Field(new FieldId("id"), "id", new FieldType.StringType(), false, Optional.empty())),
                false);
    }

    /** Capability instance returning an empty materialized dataset. */
    private record EmptyInstance() implements CapabilityInstance {

        @Override
        public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
            return new MaterializedDataSet(schema(), List.of(),
                    new DataSetMetadata("cli-orders", new DataSetLimits(100, 1000)), 0);
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
