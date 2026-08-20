package dev.hogwai.platform.runtime.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.runtime.load.config.ApplicationConfig;
import dev.hogwai.platform.runtime.load.config.input.InputBindingConfig;
import dev.hogwai.platform.runtime.compile.provider.LateOrdersProviderFactory;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GraphCompiler} graph validation: duplicate ids, references,
 * port connectivity, schema compatibility and cycles.
 */
class GraphCompilerValidationTest {

    private final GraphCompiler compiler = new GraphCompiler();
    private final ProviderResolver resolver = new ProviderResolver(new ServiceLoaderProviderRegistry());

    @Test
    void rejectsDuplicateCapabilityIds() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("duplicate capability id"));
                });
    }

    @Test
    void rejectsMissingCapabilityReference() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "missing", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("referenced capability does not exist"));
                });
    }

    @Test
    void rejectsMissingOutputPort() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "orders", "missing"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("referenced output port does not exist"));
                });
    }

    @Test
    void rejectsMissingInputPort() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("multi", CapabilityKind.TRANSFORM, "multi-input", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("missing", "orders", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("no matching input port on target capability"));
                });
    }

    @Test
    void rejectsRequiredInputNotConnected() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0", Map.of(), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("required input port is not connected"));
                });
    }

    @Test
    void rejectsIncompatibleSchemas() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("bad", CapabilityKind.TRANSFORM, "incompatible-schema", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "orders", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.SCHEMA_INCOMPATIBLE);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.SCHEMA_INCOMPATIBLE);
                });
    }

    @Test
    void rejectsExplicitBadInputPortOnSinglePortTarget() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("wrong", "orders", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("no matching input port on target capability"));
                });
    }

    @Test
    void rejectsTwoSourcesOnSameInputPort() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("orders-a", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("orders-b", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("multi", CapabilityKind.TRANSFORM, "multi-input", "1.0.0",
                        Map.of(), List.of(
                                new InputBindingConfig("left", "orders-a", "out"),
                                new InputBindingConfig("left", "orders-b", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::path)
                            .contains("/spec/capabilities/<key>/inputs/1");
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("input port is bound more than once"));
                });
    }

    @Test
    void rejectsSourceWithInputs() {
        LateOrdersProviderFactory factory = new LateOrdersProviderFactory();
        ProviderDescriptor descriptor = factory.descriptor();
        CapabilityNode source = CapabilityNode.mutable("a", CapabilityKind.SOURCE,
                descriptor.providerId(), descriptor.version(), factory, descriptor, Map.of());
        CapabilityNode target = CapabilityNode.mutable("b", CapabilityKind.SOURCE,
                descriptor.providerId(), descriptor.version(), factory, descriptor, Map.of());
        PortBinding binding = new PortBinding(source, target, new PortId("out"), new PortId("in"));
        target.setInputs(List.of(binding));

        List<Diagnostic> diagnostics = GraphValidator.validate(List.of(source, target));

        assertThat(diagnostics).extracting(Diagnostic::message)
                .anyMatch(m -> m.contains("source capability must not declare inputs"));
    }

    @Test
    void rejectsTransformWithoutInputs() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("late-orders", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of()));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("transform capability requires at least one input"));
                });
    }

    @Test
    void rejectsCycle() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("a", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "b", "out"))),
                TestGraphs.capability("b", CapabilityKind.TRANSFORM, "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "a", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_CYCLE_ERROR);
                    assertThat(pe.diagnostics().get(0).message()).contains("a").contains("b");
                });
    }
}