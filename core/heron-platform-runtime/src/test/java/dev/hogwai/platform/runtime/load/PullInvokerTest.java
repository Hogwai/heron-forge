package dev.hogwai.platform.runtime.load;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.execution.PullInvoker;
import dev.hogwai.platform.runtime.execution.StructuredPayloadProjector;
import dev.hogwai.platform.runtime.snapshot.RuntimeSnapshot;
import dev.hogwai.platform.runtime.snapshot.SnapshotBuilder;
import dev.hogwai.platform.runtime.snapshot.SnapshotCandidate;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.host.FailureCode;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.CyclomaticComplexity")
class PullInvokerTest {

    private static final Schema SCHEMA = new Schema("pull", 1,
            List.of(new Field(new FieldId("id"), "id", new FieldType.StringType(), false, Optional.empty())), false);
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");

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
            assertThat(application.execute(new InvocationRequest("read", "r1", "c1",
                    FAR_FUTURE, () -> false)).materialized())
                    .contains(StructuredPayloadProjector.project(targetData));
        }

        assertThat(order).containsExactly("source", "target");
        assertThat(sourceExecutions).hasValue(1);
        assertThat(targetExecutions).hasValue(1);
        assertThat(unrelatedExecutions).hasValue(0);
    }

    @Test
    void executesConcurrentInvocationsOnTheSameSnapshotWithCorrectResults() throws Exception {
        AtomicInteger sourceExecutions = new AtomicInteger();
        MaterializedDataSet sourceData = dataset("source");
        ProviderFactory source = factory("source", descriptor("source", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (inputs, context) -> {
                    sourceExecutions.incrementAndGet();
                    assertThat(inputs.isEmpty()).isTrue();
                    return sourceData;
                });
        ProviderRegistry registry = new Registry(source);

        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-concurrent",
                java.time.Clock.systemUTC(), SnapshotBuilderTestSupport.dataAccessFactory());
        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application(
                "pull-concurrent", new SnapshotBuilderTestYaml.TestCap("source", "source", "1.0.0")))) {
            RuntimeSnapshot snapshot = candidate.snapshot();
            PullInvoker invoker = new PullInvoker();
            ExecutionContext context = new ExecutionContext("r1", snapshot.generationId(),
                    FAR_FUTURE, () -> false, "c1");

            int threads = 2;
            CountDownLatch start = new CountDownLatch(threads);
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                List<Future<DataSet>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        start.countDown();
                        start.await();
                        return invoker.invokeTarget(snapshot, "source", context);
                    }));
                }
                for (Future<DataSet> future : futures) {
                    assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(sourceData);
                }
            } finally {
                executor.shutdownNow();
            }

            // Each invocation owns its memoization map, so the shared source runs at
            // least once per invocation; only result correctness is asserted here.
            assertThat(sourceExecutions).hasValueGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void streamedTargetKeepsItsLazyShapeThroughTheInvoker() {
        List<String> order = new ArrayList<>();
        MaterializedDataSet sourceData = dataset("source");
        ProviderFactory source = factory("source", descriptor("source", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (_, _) -> {
                    order.add("source");
                    return sourceData;
                });
        ProviderFactory target = factory("target", descriptor("target", CapabilityKind.TRANSFORM,
                        Map.of(new PortId("left"), port("left")), Map.of(new PortId("out"), port("out"))),
                (inputs, _) -> {
                    order.add("target");
                    assertThat(inputs.get(new PortId("left"))).isSameAs(sourceData);
                    return StreamingDataSet.over(
                            schema(),
                            records(7).iterator(),
                            new DataSetLimits(1000, 1_000_000),
                            3, FAR_FUTURE,
                            () -> false
                    );
                });
        ProviderRegistry registry = new Registry(source, target);

        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-stream",
                java.time.Clock.systemUTC(), SnapshotBuilderTestSupport.dataAccessFactory());
        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application(
                "pull-stream",
                new SnapshotBuilderTestYaml.TestCap("source", "source", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("target", "target", "1.0.0",
                        List.of(new SnapshotBuilderTestYaml.TestInput("left", "source", "out")))))) {
            RuntimeSnapshot snapshot = candidate.snapshot();
            PullInvoker invoker = new PullInvoker();
            ExecutionContext context = new ExecutionContext("r1", snapshot.generationId(),
                    FAR_FUTURE, () -> false, "c1");
            DataSet result = invoker.invokeTarget(snapshot, "target", context);
            assertThat(result).isInstanceOf(StreamingDataSet.class);
            try (StreamingDataSet stream = (StreamingDataSet) result) {
                List<Integer> batchSizes = new ArrayList<>();
                Optional<List<SchemaRecord>> batch = stream.nextBatch();
                while (batch.isPresent()) {
                    batchSizes.add(batch.get().size());
                    batch = stream.nextBatch();
                }
                assertThat(batchSizes).containsExactly(3, 3, 1);
                assertThat(stream.deliveredRowCount()).isEqualTo(7);
            }
            assertThat(order).containsExactly("source", "target");
        }
    }

    @Test
    void streamingUpstreamIsCollectedWhenConsumedAsInput() {
        List<String> order = new ArrayList<>();
        ProviderFactory source = factory("source", descriptor("source", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (_, _) -> {
                    order.add("source");
                    return StreamingDataSet.over(schema(), records(2).iterator(),
                            new DataSetLimits(100, 100_000), 5, FAR_FUTURE, () -> false);
                });
        ProviderFactory target = factory("target", descriptor("target", CapabilityKind.TRANSFORM,
                        Map.of(new PortId("left"), port("left")), Map.of(new PortId("out"), port("out"))),
                (inputs, _) -> {
                    order.add("target");
                    MaterializedDataSet left = inputs.get(new PortId("left"));
                    assertThat(left.records()).hasSize(2);
                    return left;
                });
        ProviderRegistry registry = new Registry(source, target);

        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-collect",
                java.time.Clock.systemUTC(), SnapshotBuilderTestSupport.dataAccessFactory());
        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application(
                "pull-collect",
                new SnapshotBuilderTestYaml.TestCap("source", "source", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("target", "target", "1.0.0",
                        List.of(new SnapshotBuilderTestYaml.TestInput("left", "source", "out")))))) {
            RuntimeSnapshot snapshot = candidate.snapshot();
            PullInvoker invoker = new PullInvoker();
            ExecutionContext context = new ExecutionContext("r1", snapshot.generationId(),
                    FAR_FUTURE, () -> false, "c1");
            DataSet result = invoker.invokeTarget(snapshot, "target", context);
            assertThat(((MaterializedDataSet) result).records()).hasSize(2);
            assertThat(order).containsExactly("source", "target");
        }
    }

    @Test
    void streamingUpstreamCursorIsClosedWhenCollectedAsInput() {
        List<String> order = new ArrayList<>();
        CloseableRowIterator rows = new CloseableRowIterator(records(2).iterator());
        ProviderFactory source = factory("source", descriptor("source", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (_, _) -> {
                    order.add("source");
                    return StreamingDataSet.over(schema(), rows,
                            new DataSetLimits(100, 100_000), 5, FAR_FUTURE, () -> false);
                });
        ProviderFactory target = factory("target", descriptor("target", CapabilityKind.TRANSFORM,
                        Map.of(new PortId("left"), port("left")), Map.of(new PortId("out"), port("out"))),
                (inputs, _) -> {
                    order.add("target");
                    MaterializedDataSet left = inputs.get(new PortId("left"));
                    assertThat(left.records()).hasSize(2);
                    return left;
                });
        ProviderRegistry registry = new Registry(source, target);

        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-collect-close",
                java.time.Clock.systemUTC(), SnapshotBuilderTestSupport.dataAccessFactory());
        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application(
                "pull-collect-close",
                new SnapshotBuilderTestYaml.TestCap("source", "source", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("target", "target", "1.0.0",
                        List.of(new SnapshotBuilderTestYaml.TestInput("left", "source", "out")))))) {
            RuntimeSnapshot snapshot = candidate.snapshot();
            PullInvoker invoker = new PullInvoker();
            ExecutionContext context = new ExecutionContext("r1", snapshot.generationId(),
                    FAR_FUTURE, () -> false, "c1");
            DataSet result = invoker.invokeTarget(snapshot, "target", context);
            assertThat(((MaterializedDataSet) result).records()).hasSize(2);
            assertThat(rows.closed).isTrue();
            assertThat(order).containsExactly("source", "target");
        }
    }

    @Test
    void materializedTargetKeepsItsShapeThroughTheInvoker() {
        MaterializedDataSet targetData = new MaterializedDataSet(schema(), records(4),
                new DataSetMetadata("target", new DataSetLimits(100, 100_000)),
                4L * StreamingDataSet.ROW_ESTIMATE_BYTES);
        ProviderFactory target = factory("target", descriptor("target", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (_, _) -> targetData);
        ProviderRegistry registry = new Registry(target);

        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-fallback",
                Clock.systemUTC(), SnapshotBuilderTestSupport.dataAccessFactory());
        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application(
                "pull-fallback", new SnapshotBuilderTestYaml.TestCap("target", "target", "1.0.0")))) {
            RuntimeSnapshot snapshot = candidate.snapshot();
            PullInvoker invoker = new PullInvoker();
            ExecutionContext context = new ExecutionContext("r1", snapshot.generationId(),
                    FAR_FUTURE, () -> false, "c1");
            assertThat(invoker.invokeTarget(snapshot, "target", context))
                    .isInstanceOf(MaterializedDataSet.class)
                    .isSameAs(targetData);
            assertThat(invoker.invokeTargetAsMaterialized(snapshot, "target", context)).isSameAs(targetData);
        }
    }

    @Test
    void streamsThroughTheHostContractOnlyForStreamingTargets() {
        MaterializedDataSet sourceData = dataset("source");
        ProviderFactory source = factory("source", descriptor("source", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (_, _) -> sourceData);
        ProviderFactory target = factory("target", descriptor("target", CapabilityKind.TRANSFORM,
                        Map.of(new PortId("left"), port("left"), new PortId("right"), port("right")),
                        Map.of(new PortId("out"), port("out"))),
                (_, _) -> StreamingDataSet.over(schema(), records(2).iterator(),
                        new DataSetLimits(100, 100_000), 5, FAR_FUTURE, () -> false));
        ProviderFactory unrelated = factory("unrelated", descriptor("unrelated", CapabilityKind.SOURCE,
                        Map.of(), Map.of(new PortId("out"), port("out"))),
                (_, _) -> dataset("unrelated"));
        ProviderRegistry registry = new Registry(source, target, unrelated);

        try (HostApplication application = load(stream(yaml()), registry,
                SnapshotBuilderTestSupport.dataAccessFactory())) {
            var streamed = application.execute(
                    new InvocationRequest("read", "r1", "c1", FAR_FUTURE, () -> false)).streaming();

            assertThat(streamed).isPresent();
            try (var payload = streamed.get()) {
                assertThat(payload.nextBatch()).hasValueSatisfying(
                        batch -> assertThat(batch).containsExactly(
                                Map.of("id", "row-0"), Map.of("id", "row-1")));
                assertThat(payload.nextBatch()).isEmpty();
                assertThat(payload.deliveredRowCount()).isEqualTo(2);
                assertThat(payload.schemaId()).isEqualTo("pull");
                assertThat(payload.schemaVersion()).isEqualTo(1);
            }

            // Unknown entrypoint surfaces as a failure outcome.
            assertThat(application.execute(new InvocationRequest("nope", "r2", "c2",
                    FAR_FUTURE, () -> false)).failure())
                    .contains(new InvocationFailure(FailureCode.ENTRYPOINT_NOT_FOUND, "entrypoint not found"));
        }
    }

    private static HostApplication load(ByteArrayInputStream yaml,
                                        ProviderRegistry registry,
                                        DataAccessFactory dataAccessFactory) {
        try {
            Method method = ApplicationLoader.class.getDeclaredMethod("load", java.io.InputStream.class,
                    ProviderRegistry.class, DataAccessFactory.class);
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
        return SCHEMA;
    }

    private static List<SchemaRecord> records(int count) {
        List<SchemaRecord> records = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            records.add(SchemaRecord.of(SCHEMA, Map.of(new FieldId("id"), "row-" + index)));
        }
        return records;
    }

    private static MaterializedDataSet dataset(String name) {
        return new MaterializedDataSet(schema(), List.of(), new DataSetMetadata(name, new DataSetLimits(100, 1000)), 0);
    }

    private static final class CloseableRowIterator implements java.util.Iterator<SchemaRecord>, AutoCloseable {

        private final java.util.Iterator<SchemaRecord> delegate;
        private boolean closed;

        private CloseableRowIterator(java.util.Iterator<SchemaRecord> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public SchemaRecord next() {
            return delegate.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static String yaml() {
        return """
                apiVersion: heron.dev/v1
                application: pull-test
                capabilities:
                  - id: source
                    provider:
                      id: source
                      version: 1.0.0
                    config:
                      host: localhost
                  - id: unrelated
                    provider:
                      id: unrelated
                      version: 1.0.0
                    config:
                      host: localhost
                  - id: target
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
                endpoints:
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
        DataSet execute(CapabilityInputs inputs, ExecutionContext context);
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
