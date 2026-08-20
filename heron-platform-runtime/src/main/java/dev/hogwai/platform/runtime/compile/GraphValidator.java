package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the structural invariants of a compiled capability graph.
 *
 * <p>Checks unique capability ids, source/transform semantics, duplicate input
 * port bindings, required input connectivity and output-to-input
 * {@link SchemaCompatibility}. All failures are reported as diagnostics with
 * stable error codes and deterministic messages that never leak configuration
 * values or secrets. Framework-independent.
 */
final class GraphValidator {

    private GraphValidator() {
        // no instances
    }

    /**
     * Validates the given nodes and their bindings.
     *
     * @param nodes the nodes to validate
     * @return an immutable list of diagnostics; empty when valid
     * @throws NullPointerException if {@code nodes} is {@code null}
     */
    static List<Diagnostic> validate(List<CapabilityNode> nodes) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        IdAndKindChecks.validate(nodes, diagnostics);
        DuplicateInputChecks.validate(nodes, diagnostics);
        InputChecks.validate(nodes, diagnostics);
        return List.copyOf(diagnostics);
    }

    /**
     * Private nested validator for unique ids and source/transform semantics.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class IdAndKindChecks {

        private IdAndKindChecks() {
            // no instances
        }

        static void validate(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            validateUniqueIds(nodes, diagnostics);
            validateKindRules(nodes, diagnostics);
        }

        private static void validateUniqueIds(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            Set<String> seen = new HashSet<>();
            for (CapabilityNode node : nodes) {
                if (!seen.add(node.id())) {
                    diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                            "/spec/capabilities/<key>/id", "duplicate capability id", "use a unique capability id"));
                }
            }
        }

        private static void validateKindRules(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            for (CapabilityNode node : nodes) {
                if (node.kind() == CapabilityKind.SOURCE && !node.inputs().isEmpty()) {
                    diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                            "/spec/capabilities/<key>/inputs", "source capability must not declare inputs",
                            "remove the input bindings"));
                }
                if (node.kind() == CapabilityKind.TRANSFORM && node.inputs().isEmpty()) {
                    diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                            "/spec/capabilities/<key>/inputs", "transform capability requires at least one input",
                            "add an input binding"));
                }
            }
        }
    }

    /**
     * Private nested validator for duplicate input port bindings.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class DuplicateInputChecks {

        private DuplicateInputChecks() {
            // no instances
        }

        static void validate(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            for (CapabilityNode node : nodes) {
                Set<PortId> bound = new HashSet<>();
                for (int i = 0; i < node.inputs().size(); i++) {
                    PortId inputPort = node.inputs().get(i).inputPort();
                    if (!bound.add(inputPort)) {
                        diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                "/spec/capabilities/<key>/inputs/" + i, "input port is bound more than once",
                                "bind each input port to a single source"));
                    }
                }
            }
        }
    }

    /**
     * Private nested validator for required input connectivity and schema
     * compatibility.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class InputChecks {

        private InputChecks() {
            // no instances
        }

        static void validate(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            validateRequiredInputs(nodes, diagnostics);
            validateSchemaCompatibility(nodes, diagnostics);
        }

        private static void validateRequiredInputs(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            for (CapabilityNode node : nodes) {
                Set<PortId> bound = new HashSet<>();
                for (PortBinding binding : node.inputs()) {
                    bound.add(binding.inputPort());
                }
                for (Map.Entry<PortId, PortDescriptor> entry : node.inputPorts().entrySet()) {
                    if (entry.getValue().required() && !bound.contains(entry.getKey())) {
                        diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                "/spec/capabilities/<key>/inputs", "required input port is not connected",
                                "connect the required input port"));
                    }
                }
            }
        }

        private static void validateSchemaCompatibility(List<CapabilityNode> nodes, List<Diagnostic> diagnostics) {
            for (CapabilityNode node : nodes) {
                for (PortBinding binding : node.inputs()) {
                    SchemaCompatibilityResult result = SchemaCompatibility.check(
                            binding.outputPortDescriptor().schema(), binding.inputPortDescriptor().schema());
                    if (!result.compatible()) {
                        diagnostics.addAll(result.diagnostics());
                    }
                }
            }
        }
    }
}
