package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import org.junit.jupiter.api.Test;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.execution.ExecutionContext;

/** Contract tests for the streaming factory/instance pair. */
class StreamingProviderContractTest {

    private static final Schema SCHEMA = new Schema("s", 1,
            List.of(new Field(new FieldId("id"), "id", new FieldType.StringType(), false, Optional.empty())),
            false);

    @Test
    void streamingFactoryIsASelfContainedContract() {
        StreamingProviderFactory factory = new StreamingProviderFactory() {
            @Override
            public ProviderDescriptor descriptor() {
                return buildDescriptor();
            }

            @Override
            public List<dev.hogwai.platform.spi.Diagnostic> validate(Map<String, Object> rawConfig) {
                return List.of();
            }

            @Override
            public StreamingInstance create(Map<String, Object> rawConfig, BuildContext context) {
                return (inputs, executionContext) -> stream();
            }

            private ProviderDescriptor buildDescriptor() {
                return new ProviderDescriptor(new ProviderId("s"), ProviderVersion.parse("1.0.0"),
                        CapabilityKind.SOURCE, SpiMajor.V1, Map.of(),
                        Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), SCHEMA, true)),
                        new ConfigurationSchema(Set.of(), Set.of(), Map.of(), Map.of()));
            }
        };

        assertThat(factory.descriptor().providerId().value()).isEqualTo("s");
        assertThat(factory.validate(Map.of())).isEmpty();
        assertThat(factory.create(null, null)).isInstanceOf(StreamingInstance.class);
    }

    @Test
    void streamingInstanceExecutesToALazyDataset() {
        StreamingInstance instance = (inputs, context) -> stream();

        ExecutionContext context = new ExecutionContext("r1", "snap-1",
                Instant.parse("2099-01-01T00:00:00Z"), () -> false, "c1");
        try (StreamingDataSet stream = instance.execute(CapabilityInputs.of(Map.of()), context)) {
            assertThat(stream.nextBatch()).hasValueSatisfying(
                    batch -> assertThat(batch).hasSize(1));
            assertThat(stream.nextBatch()).isEmpty();
        }
    }

    private static StreamingDataSet stream() {
        return StreamingDataSet.over(SCHEMA,
                List.of(SchemaRecord.of(SCHEMA, Map.of(new FieldId("id"), "row-0"))).iterator(),
                new DataSetLimits(100, 100_000), 8,
                Instant.parse("2099-01-01T00:00:00Z"), () -> false);
    }
}
