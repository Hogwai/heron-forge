package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.runtime.compile.provider.LateOrdersProviderFactory;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.InputBindingConfig;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                TestGraphs.capability("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("orders", "orders", "1.0.0",
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
                TestGraphs.capability("late-orders", "late-orders", "1.0.0",
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
                TestGraphs.capability("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", "late-orders", "1.0.0",
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
                TestGraphs.capability("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("multi", "multi-input", "1.0.0",
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
                TestGraphs.capability("late-orders", "late-orders", "1.0.0", Map.of(), List.of()));

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
                TestGraphs.capability("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("bad", "incompatible-schema", "1.0.0",
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
                TestGraphs.capability("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("late-orders", "late-orders", "1.0.0",
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
                TestGraphs.capability("orders-a", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("orders-b", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("multi", "multi-input", "1.0.0",
                        Map.of(), List.of(
                                new InputBindingConfig("left", "orders-a", "out"),
                                new InputBindingConfig("left", "orders-b", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::path)
                            .contains("/capabilities/<key>/inputs/<key>");
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
                TestGraphs.capability("late-orders", "late-orders", "1.0.0",
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
                TestGraphs.capability("a", "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "b", "out"))),
                TestGraphs.capability("b", "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "a", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_CYCLE_ERROR);
                    assertThat(pe.diagnostics().getFirst().message()).contains("a").contains("b");
                });
    }

    @Test
    void preservesProviderWarningWhenCycleCompilationFails() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("warning", "warning-provider", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("a", "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "b", "out"))),
                TestGraphs.capability("b", "late-orders", "1.0.0",
                        Map.of(), List.of(new InputBindingConfig("in", "a", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.GRAPH_CYCLE_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::severity)
                            .contains(Severity.WARNING, Severity.ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_CONFIG_ERROR,
                                    PlatformErrorCode.GRAPH_CYCLE_ERROR);
                });
    }

    @Test
    void preservesProviderErrorWhenCycleCompilationFails() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("invalid", "invalid-config", "1.0.0", Map.of(), List.of()),
                TestGraphs.capability("a", "late-orders", "1.0.0", Map.of(),
                        List.of(new InputBindingConfig("in", "b", "out"))),
                TestGraphs.capability("b", "late-orders", "1.0.0", Map.of(),
                        List.of(new InputBindingConfig("in", "a", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_CONFIG_ERROR,
                                    PlatformErrorCode.GRAPH_CYCLE_ERROR);
                });
    }

    @Test
    void preservesEqualProviderAndCycleDiagnostics() {
        ApplicationConfig app = TestGraphs.app(
                TestGraphs.capability("duplicate", "duplicate-cycle", "1.0.0",
                        Map.of("host", "localhost"), List.of()),
                TestGraphs.capability("a", "late-orders", "1.0.0", Map.of(),
                        List.of(new InputBindingConfig("in", "b", "out"))),
                TestGraphs.capability("b", "late-orders", "1.0.0", Map.of(),
                        List.of(new InputBindingConfig("in", "a", "out"))));

        assertThatThrownBy(() -> compiler.compile(app, resolver))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.diagnostics())
                            .filteredOn(diagnostic -> diagnostic.code() == PlatformErrorCode.GRAPH_CYCLE_ERROR)
                            .hasSize(2);
                });
    }
}
