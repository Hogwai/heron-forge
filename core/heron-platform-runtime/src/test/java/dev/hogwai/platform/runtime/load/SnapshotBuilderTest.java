package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.snapshot.SnapshotBuilder;
import dev.hogwai.platform.runtime.snapshot.SnapshotCandidate;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SnapshotBuilder}.
 *
 * <p>Uses in-memory trusted provider factories that create real
 * {@link CapabilityInstance}s with observable
 * close behavior, so teardown order and cleanup are asserted against real
 * instances rather than mocks.
 */
class SnapshotBuilderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsYamlGraphInstancesSnapshotPipelineWithExactIds() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory orders =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("orders", closeOrder, false));
        SnapshotBuilderTestProviderFactory late =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("late", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("late", closeOrder, false));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()),
                new ProviderRegistry.Registration(late, late.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("orders", "orders", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("late", "late", "1.0.0")))) {
            assertThat(candidate.snapshot().generationId()).isEqualTo("gen-1");
            assertThat(candidate.snapshot().instanceFactories()).containsOnlyKeys("orders", "late");
            assertThat(candidate.snapshot().instanceFactories().keySet())
                    .isEqualTo(candidate.snapshot().graph().nodeIds());
            assertThat(closeOrder).isEmpty();
        }
        assertThat(closeOrder).containsExactly("late", "orders");
    }

    @Test
    void candidateExposesBuiltSnapshotAndClosesItsResources() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory orders =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("orders", closeOrder, false));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        try (SnapshotCandidate candidate = builder.build(SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("orders", "orders", "1.0.0")))) {
            assertThat(candidate.snapshot().instanceFactories().keySet())
                    .containsExactlyElementsOf(candidate.snapshot().graph().nodeIds());
            assertThat(closeOrder).isEmpty();
        }
        assertThat(closeOrder).containsExactly("orders");
    }

    @Test
    void nullApplicationFailsWithoutCreatingInstances() {
        SnapshotBuilderTestProviderFactory orders =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("orders", new ArrayList<>(), false));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(null))
                .isInstanceOf(NullPointerException.class);

        assertThat(orders.created()).isEmpty();
    }

    @Test
    void missingProviderFailsWithoutCreatingInstances() {
        SnapshotBuilderTestProviderFactory orders =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("orders", new ArrayList<>(), false));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("orders", "missing", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_NOT_FOUND));

        assertThat(orders.created()).isEmpty();
    }

    @Test
    void providerValidationErrorFailsWithoutCreatingInstances() {
        SnapshotBuilderTestProviderFactory orders =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("orders", new ArrayList<>(), false),
                        List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                                "bad config", null)));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("orders", "orders", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR));

        assertThat(orders.created()).isEmpty();
    }

    @Test
    void graphReferenceErrorFailsWithoutCreatingInstances() {
        SnapshotBuilderTestProviderFactory t =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.transform("t", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("t", new ArrayList<>(), false));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(t, t.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("t", "t", "1.0.0",
                        List.of(new SnapshotBuilderTestYaml.TestInput("in", "missing", "out"))));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR));

        assertThat(t.created()).isEmpty();
    }

    @Test
    void createFailureClosesAlreadyCreatedInstancesInReverseOrder() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory a =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("a", closeOrder, false));
        SnapshotBuilderTestProviderFactory b =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("b", closeOrder, false));
        SnapshotBuilderTestProviderFactory c =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("c", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("create failed");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()),
                new ProviderRegistry.Registration(c, c.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("a", "a", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("b", "b", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("c", "c", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.getCause()).isNull();
                });

        assertThat(closeOrder).containsExactly("b", "a");
    }

    @Test
    void createFailureSanitizesProviderExceptionDetails() {
        SnapshotBuilderTestProviderFactory secretProvider =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("secret", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("SECRET_SENTINEL");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(secretProvider, secretProvider.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("secret", "secret", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.getMessage()).doesNotContain("SECRET_SENTINEL");
                    assertThat(pe.diagnostics().toString()).doesNotContain("SECRET_SENTINEL");
                    assertThat(pe.getCause()).isNull();
                    assertThat(pe.getSuppressed()).isEmpty();
                });
    }

    @Test
    void createNullIsPublicErrorAndCleansUp() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory a =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("a", closeOrder, false));
        SnapshotBuilderTestProviderFactory b =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> null);
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("a", "a", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("b", "b", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR));

        assertThat(closeOrder).containsExactly("a");
    }

    @Test
    void closeFailureDoesNotPreventOtherClosesAndIsSuppressed() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory a =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("a", closeOrder, true));
        SnapshotBuilderTestProviderFactory b =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("b", closeOrder, false));
        SnapshotBuilderTestProviderFactory c =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("c", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("create failed");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()),
                new ProviderRegistry.Registration(c, c.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("a", "a", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("b", "b", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("c", "c", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.getCause()).isNull();
                    assertThat(pe.getSuppressed()).hasSize(1);
                    assertThat(pe.getSuppressed()[0]).isInstanceOf(PlatformException.class)
                            .hasMessage("Platform error CAPABILITY_EXECUTION_ERROR with 1 diagnostic(s)");
                });

        // Both a and b closed (b first, then a) despite a's close throwing.
        assertThat(closeOrder).containsExactly("b", "a");
    }

    @Test
    void multipleCloseFailuresAreAttemptedAndSanitized() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory a =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("a", closeOrder, true,
                                "CLEANUP_SECRET_A"));
        SnapshotBuilderTestProviderFactory b =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("b", closeOrder, true,
                                "CLEANUP_SECRET_B"));
        SnapshotBuilderTestProviderFactory c =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("c", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("PRIMARY_SECRET_SENTINEL");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()),
                new ProviderRegistry.Registration(c, c.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        var failingApplication = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("a", "a", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("b", "b", "1.0.0"),
                new SnapshotBuilderTestYaml.TestCap("c", "c", "1.0.0"));
        assertThatThrownBy(() -> builder.build(failingApplication))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.getMessage()).doesNotContain("PRIMARY_SECRET_SENTINEL", "CLEANUP_SECRET_A",
                            "CLEANUP_SECRET_B");
                    assertThat(pe.diagnostics().toString()).doesNotContain("PRIMARY_SECRET_SENTINEL",
                            "CLEANUP_SECRET_A", "CLEANUP_SECRET_B");
                    assertThat(pe.getCause()).isNull();
                    assertThat(pe.getSuppressed()).hasSize(1);
                    PlatformException cleanup = (PlatformException) pe.getSuppressed()[0];
                    assertThat(cleanup.code()).isEqualTo(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR);
                    assertThat(cleanup.getMessage()).doesNotContain("PRIMARY_SECRET_SENTINEL", "CLEANUP_SECRET_A",
                            "CLEANUP_SECRET_B");
                    assertThat(cleanup.diagnostics().toString()).doesNotContain("PRIMARY_SECRET_SENTINEL",
                            "CLEANUP_SECRET_A", "CLEANUP_SECRET_B");
                    assertThat(cleanup.getCause()).isNull();
                    assertThat(cleanup.getSuppressed()).isEmpty();
                });

        assertThat(closeOrder).containsExactly("b", "a");
    }

    @Test
    void buildContextContainsOnlyAllowedValuesAndTrackerRegistersInstances() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestProviderFactory orders =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestInstance("orders", closeOrder, false));
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        try (var _ = builder.build(SnapshotBuilderTestSupport.application("my-app",
                new SnapshotBuilderTestYaml.TestCap("orders", "orders", "1.0.0")))) {
            BuildContext ctx = orders.contexts().getFirst();
            assertThat(ctx.clock()).isSameAs(CLOCK);
            assertThat(ctx.resourceTracker()).isNotNull();
            assertThat(ctx.dataAccessFactory()).isSameAs(SnapshotBuilderTestSupport.dataAccessFactory());
        }

        // Candidate ownership closes the registered instance and is idempotent.
        assertThat(closeOrder).containsExactly("orders");
        assertThat(((SnapshotBuilderTestInstance) orders.created().getFirst()).isClosed()).isTrue();
    }

    @Test
    void providerOwnedDataAccessIsClosedBySnapshotCandidateWhenRegistered() {
        SnapshotBuilderTestDataAccessFactory dataAccessFactory =
                new SnapshotBuilderTestDataAccessFactory();
        SnapshotBuilderTestProviderFactory provider =
                new SnapshotBuilderTestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"), context -> {
                    DataAccess access = context.dataAccessFactory().open(
                            new DataAccessConfiguration("jdbc:test", "user", "password"));
                    context.resourceTracker().register(access);
                    return new SnapshotBuilderTestInstance("orders", new ArrayList<>(), false);
                });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(provider, provider.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK, dataAccessFactory);

        try (var _ = builder.build(SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("orders", "orders", "1.0.0")))) {
            assertThat(dataAccessFactory.lastOpened()).isNotNull();
            assertThat(dataAccessFactory.lastOpened().isClosed()).isFalse();
        }

        assertThat(dataAccessFactory.lastOpened().isClosed()).isTrue();
    }
}
