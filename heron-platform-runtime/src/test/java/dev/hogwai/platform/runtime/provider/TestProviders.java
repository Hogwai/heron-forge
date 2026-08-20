package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Package-private helper for building fixture provider descriptors used by the
 * Task 6 tests and ServiceLoader fixture factories.
 */
final class TestProviders {

    private TestProviders() {
        // no instances
    }

    static Schema schema(String identifier, String fieldId, FieldType type) {
        return new Schema(identifier, 1,
                List.of(new Field(new FieldId(fieldId), fieldId, type, false, Optional.empty())), false);
    }

    static PortDescriptor port(String id, Schema schema, boolean required) {
        return new PortDescriptor(new PortId(id), schema, required);
    }

    static ProviderDescriptor source(String providerId, String version, int spiMajor, String outPort,
                                     Schema outSchema, ConfigurationSchema configSchema) {
        return new ProviderDescriptor(
                new ProviderId(providerId), ProviderVersion.parse(version), CapabilityKind.SOURCE, spiMajor,
                Map.of(), Map.of(new PortId(outPort), port(outPort, outSchema, true)),
                configSchema, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE);
    }

    static ProviderDescriptor transform(String providerId, String version, int spiMajor, String inPort,
                                       Schema inSchema, String outPort, Schema outSchema,
                                       ConfigurationSchema configSchema) {
        return new ProviderDescriptor(
                new ProviderId(providerId), ProviderVersion.parse(version), CapabilityKind.TRANSFORM, spiMajor,
                Map.of(new PortId(inPort), port(inPort, inSchema, true)),
                Map.of(new PortId(outPort), port(outPort, outSchema, true)),
                configSchema, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE);
    }

    static ConfigurationSchema emptyConfigSchema() {
        return new ConfigurationSchema(Set.of(), Set.of(), Map.of(), Map.of());
    }

    static ConfigurationSchema hostConfigSchema() {
        return new ConfigurationSchema(Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
    }

    static ConfigurationSchema deprecatedHostConfigSchema() {
        return new ConfigurationSchema(Set.of("host", "old-host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING,
                        "old-host", ConfigurationSchema.ScalarKind.STRING),
                Map.of("old-host", "use host instead"));
    }
}