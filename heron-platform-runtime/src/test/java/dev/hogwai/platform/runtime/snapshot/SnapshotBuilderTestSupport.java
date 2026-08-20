package dev.hogwai.platform.runtime.snapshot;

import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.runtime.graph.GraphCompiler;
import dev.hogwai.platform.runtime.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.provider.ProviderResolver;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import dev.hogwai.platform.spi.provider.ValidationContext;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Package-private helpers and in-memory fixtures for the snapshot builder tests.
 *
 * <p>Providers are trusted in-memory factories that create real
 * {@link CapabilityInstance}s with observable close behaviour; no production
 * provider or graph code is modified.
 */
final class SnapshotBuilderTestSupport {

    private SnapshotBuilderTestSupport() {
        // no instances
    }

    static SnapshotBuilder builder(ProviderRegistry registry, String generationId, Clock clock) {
        return new SnapshotBuilder(new SafeYamlParser(), new ProviderResolver(registry), new GraphCompiler(),
                clock, () -> generationId);
    }

    static String yaml(String name, TestCap... caps) {
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: platform.dev/v1alpha1\n");
        sb.append("kind: Application\n");
        sb.append("metadata:\n");
        sb.append("  name: ").append(name).append("\n");
        sb.append("spec:\n");
        sb.append("  capabilities:\n");
        for (TestCap c : caps) {
            sb.append("    - id: ").append(c.id()).append("\n");
            sb.append("      type: ").append(c.type()).append("\n");
            sb.append("      provider:\n");
            sb.append("        id: ").append(c.providerId()).append("\n");
            sb.append("        version: ").append(c.version()).append("\n");
            if (!c.inputs().isEmpty()) {
                sb.append("      inputs:\n");
                for (TestInput in : c.inputs()) {
                    sb.append("        ").append(in.inputPort()).append(":\n");
                    sb.append("          capability: ").append(in.capability()).append("\n");
                    sb.append("          port: ").append(in.port()).append("\n");
                }
            }
            sb.append("      config:\n");
            sb.append("        host: localhost\n");
        }
        return sb.toString();
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
        return new ProviderDescriptor(
                new ProviderId(providerId), ProviderVersion.parse(version), CapabilityKind.SOURCE, SpiMajor.V1,
                Map.of(), Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), schema(), true)),
                hostConfigSchema(), true, ProviderDescriptor.ThreadSafety.THREAD_SAFE);
    }

    static ProviderDescriptor transform(String providerId, String version) {
        return new ProviderDescriptor(
                new ProviderId(providerId), ProviderVersion.parse(version), CapabilityKind.TRANSFORM, SpiMajor.V1,
                Map.of(new PortId("in"), new PortDescriptor(new PortId("in"), schema(), true)),
                Map.of(new PortId("out"), new PortDescriptor(new PortId("out"), schema(), true)),
                hostConfigSchema(), true, ProviderDescriptor.ThreadSafety.THREAD_SAFE);
    }

    record TestInput(String inputPort, String capability, String port) {
    }

    record TestCap(String id, String type, String providerId, String version, List<TestInput> inputs) {
        TestCap(String id, String type, String providerId, String version) {
            this(id, type, providerId, version, List.of());
        }
    }

    /**
     * In-memory provider factory with configurable create and validate behaviour.
     */
    static final class TestProviderFactory implements ProviderFactory {

        private final ProviderDescriptor descriptor;
        private final Function<BuildContext, CapabilityInstance> creator;
        private final List<Diagnostic> validationDiagnostics;
        private final List<BuildContext> contexts = new ArrayList<>();
        private final List<CapabilityInstance> created = new ArrayList<>();

        TestProviderFactory(ProviderDescriptor descriptor, Function<BuildContext, CapabilityInstance> creator) {
            this(descriptor, creator, List.of());
        }

        TestProviderFactory(ProviderDescriptor descriptor, Function<BuildContext, CapabilityInstance> creator,
                            List<Diagnostic> validationDiagnostics) {
            this.descriptor = descriptor;
            this.creator = creator;
            this.validationDiagnostics = validationDiagnostics;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public List<Diagnostic> validate(Map<String, Object> rawConfig, ValidationContext context) {
            return validationDiagnostics;
        }

        @Override
        public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
            contexts.add(context);
            CapabilityInstance instance = creator.apply(context);
            if (instance != null) {
                created.add(instance);
            }
            return instance;
        }

        List<BuildContext> contexts() {
            return contexts;
        }

        List<CapabilityInstance> created() {
            return created;
        }
    }

    /**
     * Real capability instance recording close order, optionally failing to close.
     */
    static final class TestInstance implements CapabilityInstance {

        private final String id;
        private final List<String> closeOrder;
        private final boolean closeThrows;
        private final String closeFailureMessage;
        private boolean closed;

        TestInstance(String id, List<String> closeOrder, boolean closeThrows) {
            this(id, closeOrder, closeThrows, "close failed: " + id);
        }

        TestInstance(String id, List<String> closeOrder, boolean closeThrows, String closeFailureMessage) {
            this.id = id;
            this.closeOrder = closeOrder;
            this.closeThrows = closeThrows;
            this.closeFailureMessage = closeFailureMessage;
        }

        @Override
        public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
            throw new UnsupportedOperationException("not used in snapshot builder tests");
        }

        @Override
        public void close() {
            closed = true;
            closeOrder.add(id);
            if (closeThrows) {
                throw new IllegalStateException(closeFailureMessage);
            }
        }

        boolean isClosed() {
            return closed;
        }
    }

    /**
     * In-memory immutable provider registry.
     */
    static final class TestRegistry implements ProviderRegistry {

        private final Map<ProviderId, Registration> registrations;

        TestRegistry(Registration... registrations) {
            Map<ProviderId, Registration> map = new LinkedHashMap<>();
            for (Registration r : registrations) {
                map.put(r.descriptor().providerId(), r);
            }
            this.registrations = map;
        }

        @Override
        public Optional<Registration> registration(ProviderId providerId) {
            return Optional.ofNullable(registrations.get(providerId));
        }

        @Override
        public Set<ProviderId> providerIds() {
            return registrations.keySet();
        }

        @Override
        public int size() {
            return registrations.size();
        }
    }
}
