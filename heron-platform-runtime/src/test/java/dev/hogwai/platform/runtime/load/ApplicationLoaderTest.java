package dev.hogwai.platform.runtime.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.host.api.EntrypointDescriptor;
import dev.hogwai.platform.host.api.CancellationSignal;
import dev.hogwai.platform.host.api.FailureCode;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.InvocationFailure;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationSuccess;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.CyclomaticComplexity")
class ApplicationLoaderTest {

    private static final Instant DEADLINE = Instant.parse("2099-01-01T00:00:00Z");

    @Test
    void loadsYamlExposesEntrypointAndInvokesTarget() {
        TestFixture fixture = sourceFixture("source");
        try (HostApplication application = load(fixture, yaml("source", "source"))) {
            assertThat(application.entrypoints()).containsExactly(
                    new EntrypointDescriptor("read", "/read"));
            assertThat(application.invoke(request("read", () -> false))).isInstanceOf(InvocationSuccess.class);
        }
        assertThat(fixture.closed).isTrue();
    }

    @Test
    void packagePrivateLoaderPropagatesExactDataAccessFactoryToProviderContext() {
        AtomicReference<BuildContext> captured = new AtomicReference<>();
        SnapshotBuilderTestProviderFactory provider =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("source", "1.0.0"), context -> {
                            captured.set(context);
                            return new SnapshotBuilderTestInstance("source", new java.util.ArrayList<>(),
                                    false);
                        });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(provider, provider.descriptor()));
        DataAccessFactory dataAccessFactory = SnapshotBuilderTestSupport.dataAccessFactory();

        try (HostApplication ignored = ApplicationLoader.load(stream(yaml("source", "source")), registry,
                dataAccessFactory)) {
            assertThat(captured).hasValueSatisfying(context ->
                    assertThat(context.dataAccessFactory()).isSameAs(dataAccessFactory));
        }
    }

    @Test
    void publicOneArgumentLoaderBuildsMinimalConfigurationWithoutDatabaseAccess() {
        try (HostApplication application = ApplicationLoader.load(stream(minimalYaml()))) {
            assertThat(application.entrypoints()).isEmpty();
        }
    }

    @Test
    void publicLoaderSurfaceDoesNotExposeLifecycleOrProviderInternals()
            throws NoSuchMethodException, ClassNotFoundException {
        assertThat(ApplicationLoader.class.getMethods())
                .filteredOn(method -> method.getDeclaringClass() == ApplicationLoader.class)
                .allSatisfy(method -> assertThat(method.getReturnType()).isNotEqualTo(SnapshotCandidate.class));
        assertThat(ApplicationLoader.class.getMethod("load", java.io.InputStream.class).getReturnType())
                .isEqualTo(HostApplication.class);
        assertThat(Modifier.isPublic(ApplicationLoader.class.getDeclaredMethod("load", java.io.InputStream.class,
                ProviderRegistry.class, DataAccessFactory.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(RuntimeApplication.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(Class.forName("dev.hogwai.platform.runtime.load.PullInvoker").getModifiers()))
                .isFalse();
    }

    @Test
    void invalidYamlAndProviderConfigurationFailBeforeReturningAnApplication() {
        TestFixture fixture = sourceFixture("source");
        assertThatThrownBy(() -> ApplicationLoader.load(stream("not: [valid"), fixture.registry,
                fixture.dataAccessFactory))
                .isInstanceOf(PlatformException.class);

        TestFixture invalid = sourceFixture("source", List.of(new Diagnostic(
                PlatformErrorCode.PROVIDER_CONFIG_ERROR, dev.hogwai.platform.spi.Severity.ERROR,
                null, "invalid", null)));
        assertThatThrownBy(() -> ApplicationLoader.load(stream(yaml("source", "source")), invalid.registry,
                invalid.dataAccessFactory))
                .isInstanceOf(PlatformException.class);
        assertThat(invalid.created.get()).isZero();
    }

    @Test
    void missingEntrypointTargetClosesAlreadyCreatedInstances() {
        TestFixture fixture = sourceFixture("source");
        assertThatThrownBy(() -> ApplicationLoader.load(stream(yaml("missing", "source")), fixture.registry,
                fixture.dataAccessFactory))
                .isInstanceOf(PlatformException.class)
                .satisfies(error -> assertThat(((PlatformException) error).code())
                        .isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR));
        assertThat(fixture.closed).isTrue();
    }

    @Test
    void mapsMissingEntrypointProviderDeadlineAndCancellationFailures() {
        TestFixture fixture = sourceFixture("source");
        try (HostApplication application = load(fixture, yaml("source", "source"))) {
            assertThat(application.invoke(request("other", () -> false)))
                    .isEqualTo(new InvocationFailure(FailureCode.ENTRYPOINT_NOT_FOUND, "entrypoint not found"));
            assertThat(application.invoke(request("read", () -> false,
                    Instant.parse("2000-01-01T00:00:00Z"))))
                    .isEqualTo(new InvocationFailure(FailureCode.DEADLINE_EXCEEDED, "deadline exceeded"));
            assertThat(application.invoke(request("read", () -> true)))
                    .isEqualTo(new InvocationFailure(FailureCode.CANCELLATION_REQUESTED,
                            "cancellation requested"));
        }

        TestFixture failing = sourceFixture("source", List.of(), context -> {
            return new CapabilityInstance() {
                @Override
                public dev.hogwai.platform.spi.data.MaterializedDataSet execute(
                        dev.hogwai.platform.spi.provider.CapabilityInputs inputs,
                        dev.hogwai.platform.spi.execution.ExecutionContext executionContext) {
                    throw new IllegalStateException("provider secret");
                }
            };
        });
        try (HostApplication application = load(failing, yaml("source", "source"))) {
            assertThat(application.invoke(request("read", () -> false)))
                    .isEqualTo(new InvocationFailure(FailureCode.PROVIDER, "provider execution failed"));
        }
    }

    @Test
    void passesTheHostCancellationSignalToExecutionContext() {
        TestFixture fixture = sourceFixture("source");
        AtomicBoolean cancelled = new AtomicBoolean();
        CancellationSignal signal = cancelled::get;
        try (HostApplication application = load(fixture, yaml("source", "source"))) {
            assertThat(application.invoke(request("read", signal))).isInstanceOf(InvocationSuccess.class);
            assertThat(fixture.lastContext.get()).isNotNull();
            assertThat(fixture.lastContext.get().cancellationToken().isCancellationRequested()).isFalse();
            cancelled.set(true);
            assertThat(fixture.lastContext.get().cancellationToken().isCancellationRequested()).isTrue();
        }
    }

    @Test
    void invocationAfterCloseIsSafeAndCloseIsIdempotent() {
        TestFixture fixture = sourceFixture("source");
        HostApplication application = load(fixture, yaml("source", "source"));
        application.close();
        application.close();
        assertThat(application.invoke(request("read", () -> false)))
                .isEqualTo(new InvocationFailure(FailureCode.INTERNAL, "application is closed"));
        assertThat(fixture.closedCount.get()).isEqualTo(1);
    }

    private static HostApplication load(TestFixture fixture, String yaml) {
        return ApplicationLoader.load(stream(yaml), fixture.registry, fixture.dataAccessFactory);
    }

    private static InvocationRequest request(String entrypoint, CancellationSignal token) {
        return request(entrypoint, token, DEADLINE);
    }

    private static InvocationRequest request(String entrypoint,
                                             CancellationSignal token,
                                             Instant deadline) {
        return new InvocationRequest(entrypoint, "request-1", "correlation-1", deadline, token);
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String yaml(String target, String capabilityId) {
        return """
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: loader-test
                spec:
                  capabilities:
                    - id: %s
                      type: source
                      provider:
                        id: source
                        version: 1.0.0
                      config:
                        host: localhost
                  entrypoints:
                    - id: read
                      method: GET
                      path: /read
                      target: %s
                """.formatted(capabilityId, target);
    }

    private static String minimalYaml() {
        return """
                apiVersion: platform.dev/v1alpha1
                kind: Application
                metadata:
                  name: minimal-loader-test
                spec:
                  capabilities: []
                """;
    }

    private static TestFixture sourceFixture(String id) {
        return sourceFixture(id, List.of());
    }

    private static TestFixture sourceFixture(String id, List<Diagnostic> diagnostics) {
        dev.hogwai.platform.spi.data.MaterializedDataSet dataset = MaterializedDataSetFactory.dataset();
        return sourceFixture(id, diagnostics, dataset, context -> new RecordingInstance(
                dataset, new AtomicInteger(), new AtomicBoolean(), null));
    }

    private static TestFixture sourceFixture(String id, List<Diagnostic> diagnostics,
                                             java.util.function.Function<dev.hogwai.platform.spi.provider.BuildContext,
                                                     CapabilityInstance> creator) {
        return sourceFixture(id, diagnostics, MaterializedDataSetFactory.dataset(), creator);
    }

    private static TestFixture sourceFixture(String id, List<Diagnostic> diagnostics,
                                             dev.hogwai.platform.spi.data.MaterializedDataSet dataset,
                                             java.util.function.Function<dev.hogwai.platform.spi.provider.BuildContext,
                                                     CapabilityInstance> creator) {
        AtomicInteger created = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext = new AtomicReference<>();
        TestFixture fixture = new TestFixture(created, closed, new AtomicInteger(),
                dataset, lastContext, SnapshotBuilderTestSupport.dataAccessFactory());
        SnapshotBuilderTestProviderFactory factory =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source(id, "1.0.0"), context -> {
                            created.incrementAndGet();
                            CapabilityInstance instance = creator.apply(context);
                            return new CloseTrackingInstance(instance, closed, fixture.closedCount,
                                    fixture.lastContext);
                        }, diagnostics);
        fixture.registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(factory, factory.descriptor()));
        return fixture;
    }

    private static final class TestFixture {
        private final AtomicInteger created;
        private final AtomicBoolean closed;
        private final AtomicInteger closedCount;
        private final dev.hogwai.platform.spi.data.MaterializedDataSet dataset;
        private final AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext;
        private final DataAccessFactory dataAccessFactory;
        private ProviderRegistry registry;

        private TestFixture(AtomicInteger created, AtomicBoolean closed, AtomicInteger closedCount,
                            dev.hogwai.platform.spi.data.MaterializedDataSet dataset,
                            AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext,
                            DataAccessFactory dataAccessFactory) {
            this.created = created;
            this.closed = closed;
            this.closedCount = closedCount;
            this.dataset = dataset;
            this.lastContext = lastContext;
            this.dataAccessFactory = dataAccessFactory;
        }
    }

    private static final class CloseTrackingInstance implements CapabilityInstance {
        private final CapabilityInstance delegate;
        private final AtomicBoolean closed;
        private final AtomicInteger closeCount;
        private final AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext;

        private CloseTrackingInstance(CapabilityInstance delegate, AtomicBoolean closed, AtomicInteger closeCount,
                                      AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext) {
            this.delegate = delegate;
            this.closed = closed;
            this.closeCount = closeCount;
            this.lastContext = lastContext;
        }

        @Override
        public dev.hogwai.platform.spi.data.MaterializedDataSet execute(
                dev.hogwai.platform.spi.provider.CapabilityInputs inputs,
                dev.hogwai.platform.spi.execution.ExecutionContext context) {
            lastContext.set(context);
            return delegate.execute(inputs, context);
        }

        @Override
        public void close() {
            closed.set(true);
            closeCount.incrementAndGet();
            delegate.close();
        }
    }

    private static final class RecordingInstance implements CapabilityInstance {
        private final dev.hogwai.platform.spi.data.MaterializedDataSet dataset;
        private final AtomicInteger executions;
        private final AtomicBoolean ignored;
        private final AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext;

        private RecordingInstance(dev.hogwai.platform.spi.data.MaterializedDataSet dataset,
                                  AtomicInteger executions, AtomicBoolean ignored,
                                  AtomicReference<dev.hogwai.platform.spi.execution.ExecutionContext> lastContext) {
            this.dataset = dataset;
            this.executions = executions;
            this.ignored = ignored;
            this.lastContext = lastContext;
        }

        @Override
        public dev.hogwai.platform.spi.data.MaterializedDataSet execute(
                dev.hogwai.platform.spi.provider.CapabilityInputs inputs,
                dev.hogwai.platform.spi.execution.ExecutionContext context) {
            executions.incrementAndGet();
            if (lastContext != null) {
                lastContext.set(context);
            }
            return dataset;
        }
    }

    private static final class MaterializedDataSetFactory {
        private static dev.hogwai.platform.spi.data.MaterializedDataSet dataset() {
            return new dev.hogwai.platform.spi.data.MaterializedDataSet(
                    SnapshotBuilderTestSupport.schema(), List.of(),
                    new dev.hogwai.platform.spi.data.DataSetMetadata("source",
                            new dev.hogwai.platform.spi.data.DataSetLimits(100, 1000)), 0);
        }
    }
}
