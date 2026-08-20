package dev.hogwai.platform.runtime.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.runtime.load.config.ApplicationConfig;
import dev.hogwai.platform.runtime.load.config.input.InputBindingConfig;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.PortId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GraphCompiler} covering provider resolution, graph
 * validation and topological sorting.
 */
class GraphCompilerTest {

    private final GraphCompiler compiler = new GraphCompiler();
    private final ProviderResolver resolver = new ProviderResolver(new ServiceLoaderProviderRegistry());

    @Test
    void compilesValidTwoNodeGraph() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "orders", "out"))));

        CapabilityGraph graph = compiler.compile(app, resolver);

        assertThat(graph.size()).isEqualTo(2);
        assertThat(graph.topologicalOrder()).containsExactly("orders", "late-orders");
        assertThat(graph.dependencies("late-orders")).containsExactly("orders");
        assertThat(graph.consumers("orders")).containsExactly("late-orders");
        assertThat(graph.node("orders")).isPresent();
        assertThat(graph.node("orders").orElseThrow().inputs()).isEmpty();
        assertThat(graph.node("late-orders").orElseThrow().inputs()).hasSize(1);

        PortBinding binding = graph.node("late-orders").orElseThrow().inputs().get(0);
        assertThat(binding.source().id()).isEqualTo("orders");
        assertThat(binding.target().id()).isEqualTo("late-orders");
        assertThat(binding.outputPort()).isEqualTo(new PortId("out"));
        assertThat(binding.inputPort()).isEqualTo(new PortId("in"));
    }

    @Test
    void rejectsMissingProvider() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "missing-provider", "1.0.0", Map.of(), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_NOT_FOUND);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_NOT_FOUND);
                });
    }

    @Test
    void rejectsExactVersionMismatch() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "9.9.9",
                        Map.of("host", "localhost"), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                });
    }

    @Test
    void rejectsSpiMajorMismatch() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "wrong-spi", "1.0.0", Map.of(), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                });
    }

    @Test
    void rejectsKindMismatch() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.TRANSFORM, "wrong-kind", "1.0.0", Map.of(), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                });
    }

    @Test
    void rejectsProviderValidationDiagnostic() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "invalid-config", "1.0.0", Map.of(), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                });
    }

    @Test
    void graphIsImmutable() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "orders", "out"))));

        CapabilityGraph graph = compiler.compile(app, resolver);

        assertThatThrownBy(() -> graph.topologicalOrder().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.dependencies("late-orders").add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.consumers("orders").add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.nodeIds().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.nodes().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void compilesValidMultiInputGraph() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders-a", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("orders-b", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("multi", CapabilityKind.TRANSFORM, "multi-input", "1.0.0",
                        Map.of(), List.of(
                                new InputBindingConfig("left", "orders-a", "out"),
                                new InputBindingConfig("right", "orders-b", "out"))));

        CapabilityGraph graph = compiler.compile(app, resolver);

        assertThat(graph.size()).isEqualTo(3);
        assertThat(graph.topologicalOrder()).containsExactly("orders-a", "orders-b", "multi");
        CapabilityNode multi = graph.node("multi").orElseThrow();
        assertThat(multi.inputs()).hasSize(2);
        assertThat(multi.inputs()).extracting(PortBinding::inputPort)
                .containsExactly(new PortId("left"), new PortId("right"));
        assertThat(multi.inputs()).extracting(binding -> binding.source().id())
                .containsExactly("orders-a", "orders-b");
    }

    @Test
    void bindingsReferenceCanonicalNodes() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "orders", "out"))));

        CapabilityGraph graph = compiler.compile(app, resolver);

        PortBinding binding = graph.node("late-orders").orElseThrow().inputs().get(0);
        assertThat(binding.source()).isSameAs(graph.node("orders").orElseThrow());
        assertThat(binding.target()).isSameAs(graph.node("late-orders").orElseThrow());
    }

    @Test
    void resolvesForwardReferencesToCanonicalNodes() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "orders", "out"))),
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()));

        CapabilityGraph graph = compiler.compile(app, resolver);

        assertThat(graph.topologicalOrder()).containsExactly("orders", "late-orders");
        PortBinding binding = graph.node("late-orders").orElseThrow().inputs().get(0);
        assertThat(binding.source()).isSameAs(graph.node("orders").orElseThrow());
        assertThat(binding.target()).isSameAs(graph.node("late-orders").orElseThrow());
    }

    @Test
    void preservesResolverWarningsAndGraphError() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("warn", CapabilityKind.SOURCE, "warning-provider", "1.0.0",
                        Map.of("host", "localhost", "old-host", "legacy"), List.of()),
                TestGraphs.capability("bad", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "missing", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .contains("configuration field is deprecated",
                                    "provider configuration warning",
                                    "referenced capability does not exist");
                    assertThat(pe.diagnostics()).extracting(Diagnostic::path)
                            .contains("/spec/capabilities/0/config/old-host",
                                    "/spec/capabilities/0/config",
                                    "/spec/capabilities/1/inputs/0/capability");
                });
    }
}