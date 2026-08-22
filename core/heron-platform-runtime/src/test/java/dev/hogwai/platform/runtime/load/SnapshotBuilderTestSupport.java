package dev.hogwai.platform.runtime.load;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.snapshot.SnapshotBuilder;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/** Shared descriptor, graph, and data-access setup for snapshot builder tests. */
final class SnapshotBuilderTestSupport {

    private static final DataAccessFactory DATA_ACCESS_FACTORY = new SnapshotBuilderTestDataAccessFactory();

    private SnapshotBuilderTestSupport() {
    }

    static SnapshotBuilder builder(ProviderRegistry registry, String generationId, Clock clock) {
        return builder(registry, generationId, clock, DATA_ACCESS_FACTORY);
    }

    static SnapshotBuilder builder(ProviderRegistry registry, String generationId, Clock clock,
            DataAccessFactory dataAccessFactory) {
        return new SnapshotBuilder(new ProviderResolver(registry), new GraphCompiler(),
                clock, dataAccessFactory, () -> generationId);
    }

    static DataAccessFactory dataAccessFactory() {
        return DATA_ACCESS_FACTORY;
    }

    static ApplicationConfig application(String name, SnapshotBuilderTestYaml.TestCap... caps) {
        return SnapshotBuilderTestYaml.application(name, caps);
    }

    static String yaml(String name, SnapshotBuilderTestYaml.TestCap... caps) {
        return SnapshotBuilderTestYaml.yaml(name, caps);
    }

    static Schema schema() {
        return new Schema("out", 1,
                List.of(new Field(new FieldId("id"), "id", new FieldType.StringType(), false, Optional.empty())), false);
    }

    static ConfigurationSchema hostConfigSchema() {
        return new ConfigurationSchema(Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
    }

    static ProviderDescriptor source(String providerId, String version) {
        return new ProviderDescriptor(new ProviderId(providerId), ProviderVersion.parse(version), CapabilityKind.SOURCE,
                SpiMajor.V1, Map.of(),
                Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), schema(), true)),
                hostConfigSchema());
    }

    static ProviderDescriptor transform(String providerId, String version) {
        return new ProviderDescriptor(new ProviderId(providerId), ProviderVersion.parse(version),
                CapabilityKind.TRANSFORM, SpiMajor.V1,
                Map.of(new PortId("in"), new PortDescriptor(new PortId("in"), schema(), true)),
                Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), schema(), true)),
                hostConfigSchema());
    }
}
