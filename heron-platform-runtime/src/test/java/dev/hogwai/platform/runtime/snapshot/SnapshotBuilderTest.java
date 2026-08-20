package dev.hogwai.platform.runtime.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.runtime.provider.ProviderRegistry;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SnapshotBuilder}.
 *
 * <p>Uses in-memory trusted provider factories that create real
 * {@link dev.hogwai.platform.spi.provider.CapabilityInstance}s with observable
 * close behaviour, so teardown order and cleanup are asserted against real
 * instances rather than mocks.
 */
class SnapshotBuilderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsYamlGraphInstancesSnapshotPipelineWithExactIds() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestSupport.TestProviderFactory orders =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("orders", closeOrder, false));
        SnapshotBuilderTestSupport.TestProviderFactory late =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("late", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("late", closeOrder, false));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()),
                new ProviderRegistry.Registration(late, late.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        try (SnapshotCandidate candidate = builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("orders", "source", "orders", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("late", "source", "late", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8)))) {
            assertThat(candidate.snapshot().generationId()).isEqualTo("gen-1");
            assertThat(candidate.snapshot().instances()).containsOnlyKeys("orders", "late");
            assertThat(candidate.snapshot().instances().keySet())
                    .isEqualTo(candidate.snapshot().graph().nodeIds());
            assertThat(closeOrder).isEmpty();
        }
        assertThat(closeOrder).containsExactly("late", "orders");
    }

    @Test
    void candidateExposesBuiltSnapshotAndClosesItsResources() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestSupport.TestProviderFactory orders =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("orders", closeOrder, false));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        try (SnapshotCandidate candidate = builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("orders", "source", "orders", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8)))) {
            assertThat(candidate.snapshot().instances().keySet())
                    .containsExactlyElementsOf(candidate.snapshot().graph().nodeIds());
            assertThat(closeOrder).isEmpty();
        }
        assertThat(closeOrder).containsExactly("orders");
    }

    @Test
    void invalidYamlFailsWithoutCreatingInstances() {
        SnapshotBuilderTestSupport.TestProviderFactory orders =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("orders", new ArrayList<>(), false));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream("not: [valid".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PlatformException.class);

        assertThat(orders.created()).isEmpty();
    }

    @Test
    void missingProviderFailsWithoutCreatingInstances() {
        SnapshotBuilderTestSupport.TestProviderFactory orders =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("orders", new ArrayList<>(), false));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("orders", "source", "missing", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_NOT_FOUND));

        assertThat(orders.created()).isEmpty();
    }

    @Test
    void providerValidationErrorFailsWithoutCreatingInstances() {
        SnapshotBuilderTestSupport.TestProviderFactory orders =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("orders", new ArrayList<>(), false),
                        List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                                "bad config", null)));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("orders", "source", "orders", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR));

        assertThat(orders.created()).isEmpty();
    }

    @Test
    void graphReferenceErrorFailsWithoutCreatingInstances() {
        SnapshotBuilderTestSupport.TestProviderFactory t =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.transform("t", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("t", new ArrayList<>(), false));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(t, t.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("t", "transform", "t", "1.0.0",
                                List.of(new SnapshotBuilderTestSupport.TestInput("in", "missing", "out"))))
                        .getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR));

        assertThat(t.created()).isEmpty();
    }

    @Test
    void createFailureClosesAlreadyCreatedInstancesInReverseOrder() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestSupport.TestProviderFactory a =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("a", closeOrder, false));
        SnapshotBuilderTestSupport.TestProviderFactory b =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("b", closeOrder, false));
        SnapshotBuilderTestSupport.TestProviderFactory c =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("c", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("create failed");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()),
                new ProviderRegistry.Registration(c, c.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("a", "source", "a", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("b", "source", "b", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("c", "source", "c", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
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
        SnapshotBuilderTestSupport.TestProviderFactory secretProvider =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("secret", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("SECRET_SENTINEL");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(secretProvider, secretProvider.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("secret", "source", "secret", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
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
        SnapshotBuilderTestSupport.TestProviderFactory a =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("a", closeOrder, false));
        SnapshotBuilderTestSupport.TestProviderFactory b =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> null);
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("a", "source", "a", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("b", "source", "b", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR));

        assertThat(closeOrder).containsExactly("a");
    }

    @Test
    void closeFailureDoesNotPreventOtherClosesAndIsSuppressed() {
        List<String> closeOrder = new ArrayList<>();
        SnapshotBuilderTestSupport.TestProviderFactory a =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("a", closeOrder, true));
        SnapshotBuilderTestSupport.TestProviderFactory b =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("b", closeOrder, false));
        SnapshotBuilderTestSupport.TestProviderFactory c =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("c", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("create failed");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()),
                new ProviderRegistry.Registration(c, c.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("a", "source", "a", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("b", "source", "b", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("c", "source", "c", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
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
        SnapshotBuilderTestSupport.TestProviderFactory a =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("a", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("a", closeOrder, true,
                                "CLEANUP_SECRET_A"));
        SnapshotBuilderTestSupport.TestProviderFactory b =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("b", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("b", closeOrder, true,
                                "CLEANUP_SECRET_B"));
        SnapshotBuilderTestSupport.TestProviderFactory c =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("c", "1.0.0"),
                        ctx -> {
                            throw new IllegalStateException("PRIMARY_SECRET_SENTINEL");
                        });
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(a, a.descriptor()),
                new ProviderRegistry.Registration(b, b.descriptor()),
                new ProviderRegistry.Registration(c, c.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        assertThatThrownBy(() -> builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("test",
                        new SnapshotBuilderTestSupport.TestCap("a", "source", "a", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("b", "source", "b", "1.0.0"),
                        new SnapshotBuilderTestSupport.TestCap("c", "source", "c", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8))))
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
        SnapshotBuilderTestSupport.TestProviderFactory orders =
                new SnapshotBuilderTestSupport.TestProviderFactory(
                        SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                        ctx -> new SnapshotBuilderTestSupport.TestInstance("orders", closeOrder, false));
        ProviderRegistry registry = new SnapshotBuilderTestSupport.TestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        try (SnapshotCandidate candidate = builder.build(new ByteArrayInputStream(
                SnapshotBuilderTestSupport.yaml("my-app",
                        new SnapshotBuilderTestSupport.TestCap("orders", "source", "orders", "1.0.0"))
                        .getBytes(StandardCharsets.UTF_8)))) {
            BuildContext ctx = orders.contexts().get(0);
            assertThat(ctx.applicationId()).isEqualTo("my-app");
            assertThat(ctx.snapshotId()).isEqualTo("gen-1");
            assertThat(ctx.clock()).isSameAs(CLOCK);
            assertThat(ctx.resourceTracker()).isNotNull();
        }

        // Candidate ownership closes the registered instance and is idempotent.
        assertThat(closeOrder).containsExactly("orders");
        assertThat(((SnapshotBuilderTestSupport.TestInstance) orders.created().get(0)).isClosed()).isTrue();
    }
}
