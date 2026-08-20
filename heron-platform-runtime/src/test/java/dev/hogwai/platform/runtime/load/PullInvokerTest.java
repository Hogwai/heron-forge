package dev.hogwai.platform.runtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationSuccess;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.CyclomaticComplexity")
class PullInvokerTest {

    @Test
    void pullsOnlyTheTransitiveClosureInDependencyOrderAndMemoizesSharedSources() {
        List<String> order = new ArrayList<>();
        AtomicInteger sourceExecutions = new AtomicInteger();
        AtomicInteger unrelatedExecutions = new AtomicInteger();
        AtomicInteger targetExecutions = new AtomicInteger();
        MaterializedDataSet sourceData = dataset("source");
        MaterializedDataSet unrelatedData = dataset("unrelated");
        MaterializedDataSet targetData = dataset("target");

        ProviderFactory source = factory("source", descriptor("source", CapabilityKind.SOURCE,
                Map.of(), Map.of(new PortId("out"), port("out"))),
                (inputs, context) -> {
                    order.add("source");
                    sourceExecutions.incrementAndGet();
                    assertThat(inputs.isEmpty()).isTrue();
                    return sourceData;
                });
        ProviderFactory unrelated = factory("unrelated", descriptor("unrelated", CapabilityKind.SOURCE,
                Map.of(), Map.of(new PortId("out"), port("out"))),
                (inputs, context) -> {
                    unrelatedExecutions.incrementAndGet();
                    return unrelatedData;
                });
        ProviderFactory target = factory("target", descriptor("target", CapabilityKind.TRANSFORM,
                Map.of(new PortId("left"), port("left"), new PortId("right"), port("right")),
                Map.of(new PortId("out"), port("out"))),
                (inputs, context) -> {
                    order.add("target");
                    targetExecutions.incrementAndGet();
                    assertThat(inputs.portIds()).containsExactlyInAnyOrder(new PortId("left"), new PortId("right"));
                    assertThat(inputs.get(new PortId("left"))).isSameAs(sourceData);
                    assertThat(inputs.get(new PortId("right"))).isSameAs(sourceData);
                    return targetData;
                });
        ProviderRegistry registry = new Registry(source, unrelated, target);

        try (HostApplication application = load(stream(yaml()), registry,
                SnapshotBuilderTestSupport.dataAccessFactory())) {
            assertThat(application.invoke(new InvocationRequest("read", "r1", "c1",
                    Instant.parse("2099-01-01T00:00:00Z"), () -> false)))
                    .isEqualTo(new InvocationSuccess(StructuredPayloadProjector.project(targetData)));
        }

        assertThat(order).containsExactly("source", "target");
        assertThat(sourceExecutions).hasValue(1);
        assertThat(targetExecutions).hasValue(1);
        assertThat(unrelatedExecutions).hasValue(0);
    }

    private static HostApplication load(ByteArrayInputStream yaml, ProviderRegistry registry,
                                        dev.hogwai.platform.spi.data.access.DataAccessFactory dataAccessFactory) {
        try {
            Method method = ApplicationLoader.class.getDeclaredMethod("load", java.io.InputStream.class,
                    ProviderRegistry.class, dev.hogwai.platform.spi.data.access.DataAccessFactory.class);
            method.setAccessible(true);
            return (HostApplication) method.invoke(null, yaml, registry, dataAccessFactory);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ProviderFactory factory(String id, ProviderDescriptor descriptor,
                                           CapabilityExecution execution) {
        java.util.Objects.requireNonNull(id, "id must not be null");
        return new ProviderFactory() {
            @Override
            public ProviderDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public List<dev.hogwai.platform.spi.Diagnostic> validate(Map<String, Object> config) {
                return List.of();
            }

            @Override
            public CapabilityInstance create(Map<String, Object> config,
                                             dev.hogwai.platform.spi.provider.BuildContext context) {
                return execution::execute;
            }
        };
    }

    private static ProviderDescriptor descriptor(String id, CapabilityKind kind,
                                                 Map<PortId, PortDescriptor> inputs,
                                                 Map<PortId, PortDescriptor> outputs) {
        return new ProviderDescriptor(new ProviderId(id), ProviderVersion.parse("1.0.0"), kind,
                SpiMajor.V1, inputs, outputs,
                new ConfigurationSchema(Set.of("host"), Set.of("host"),
                        Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of()));
    }

    private static PortDescriptor port(String id) {
        return new PortDescriptor(new PortId(id), schema(), true);
    }

    private static Schema schema() {
        return new Schema("pull", 1,
                List.of(new Field(new FieldId("id"), "id", new FieldType.StringType(), false, Optional.empty())), false);
    }

    private static MaterializedDataSet dataset(String name) {
        return new MaterializedDataSet(schema(), List.of(), new DataSetMetadata(name, new DataSetLimits(100, 1000)), 0);
    }

    private static String yaml() {
        return """
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: pull-test
                spec:
                  capabilities:
                    - id: source
                      type: source
                      provider:
                        id: source
                        version: 1.0.0
                      config:
                        host: localhost
                    - id: unrelated
                      type: source
                      provider:
                        id: unrelated
                        version: 1.0.0
                      config:
                        host: localhost
                    - id: target
                      type: transform
                      provider:
                        id: target
                        version: 1.0.0
                      config:
                        host: localhost
                      inputs:
                        left:
                          capability: source
                          port: out
                        right:
                          capability: source
                          port: out
                  entrypoints:
                    - id: read
                      method: GET
                      path: /read
                      target: target
                """;
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface CapabilityExecution {
        MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context);
    }

    private static final class Registry implements ProviderRegistry {
        private final Map<ProviderId, Registration> registrations;

        private Registry(ProviderFactory... factories) {
            this.registrations = java.util.Arrays.stream(factories)
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            factory -> factory.descriptor().providerId(),
                            factory -> new Registration(factory, factory.descriptor())));
        }

        @Override
        public Optional<Registration> registration(ProviderId providerId) {
            return Optional.ofNullable(registrations.get(providerId));
        }

    }
}
