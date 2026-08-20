package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderFactoryTest {

    private static final DataAccessFactory DATA_ACCESS_FACTORY = configuration -> new DataAccess() {
        @Override
        public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
            return List.of();
        }

        @Override
        public void close() {
            // no resources
        }
    };

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            new ProviderId("acme"), ProviderVersion.parse("1.0.0"), CapabilityKind.SOURCE, SpiMajor.V1,
            Map.of(), Map.of(new PortId("out"), ProviderTestSupport.port("out")),
            new ConfigurationSchema(Set.of("host"), Set.of("host"),
                    Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of()));

    /** A well-behaved factory that decodes the raw config itself. */
    private static final class FakeFactory implements ProviderFactory {
        @Override
        public ProviderDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public List<Diagnostic> validate(Map<String, Object> rawConfig) {
            if (!"good".equals(rawConfig.get("host"))) {
                return List.of(Diagnostic.of(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR,
                        "invalid host"));
            }
            return List.of();
        }

        @Override
        public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
            return new CapabilityInstance() {
                @Override
                public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext ctx) {
                    return new MaterializedDataSet(ProviderTestSupport.schema("s"), List.of(),
                            new dev.hogwai.platform.spi.data.DataSetMetadata("ds",
                                    new dev.hogwai.platform.spi.data.DataSetLimits(10, 1000)), 0);
                }
            };
        }
    }

    @Test
    void exposesDescriptor() {
        assertThat(new FakeFactory().descriptor()).isSameAs(DESCRIPTOR);
    }

    @Test
    void validateReturnsDiagnosticsForInvalidConfig() {
        List<Diagnostic> diagnostics = new FakeFactory().validate(Map.of("host", "bad"));
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
    }

    @Test
    void validateReturnsEmptyForValidConfig() {
        List<Diagnostic> diagnostics = new FakeFactory().validate(Map.of("host", "good"));
        assertThat(diagnostics).isEmpty();
    }

    @Test
    void createReturnsCapabilityInstance() {
        CapabilityInstance instance = new FakeFactory().create(Map.of("host", "good"),
                new BuildContext(java.time.Clock.systemUTC(), resource -> { }, DATA_ACCESS_FACTORY));
        assertThat(instance).isNotNull();
    }
}
