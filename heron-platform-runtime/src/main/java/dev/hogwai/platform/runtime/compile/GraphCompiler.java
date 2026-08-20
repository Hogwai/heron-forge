package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.runtime.load.config.ApplicationConfig;
import dev.hogwai.platform.runtime.load.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.load.config.input.InputBindingConfig;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver.ResolvedProvider;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.Severity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles an {@link ApplicationConfig} into an immutable {@link CapabilityGraph}.
 *
 * <p>Providers are resolved with exact version, SPI major and capability kind
 * before compilation. Each input binding is resolved to a named output port on
 * an existing source capability and the explicitly named input port on the
 * target; no heuristic infers the target input port. Edges are first staged by
 * immutable ids and ports, then resolved against the canonical node map so that
 * every {@link PortBinding} references the exact node instances later exposed
 * by the graph, including forward references. The resulting graph is validated
 * for unique ids, source/transform semantics, duplicate input port bindings,
 * required input connectivity and schema compatibility before the immutable
 * graph is built. Framework-independent.
 */
public final class GraphCompiler {

    /**
     * Compiles the given application configuration into an immutable graph.
     *
     * @param application the application configuration
     * @param resolver    the provider resolver
     * @return the immutable compiled graph
     * @throws NullPointerException if {@code application} or {@code resolver} is
     *                              {@code null}
     * @throws PlatformException    if provider resolution, graph validation or
     *                              topological sorting fails
     */
    public CapabilityGraph compile(ApplicationConfig application, ProviderResolver resolver) {
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");

        List<Diagnostic> diagnostics = new ArrayList<>();
        List<CapabilityNode> nodes = new ArrayList<>();
        Map<String, CapabilityNode> nodeById = new LinkedHashMap<>();

        NodeBuilder.resolveProviders(application, resolver, nodes, nodeById, diagnostics);
        List<StagedEdge> edges = EdgeStager.stage(application, nodeById, diagnostics);
        NodeFinalizer.finalizeNodes(nodeById, edges);
        diagnostics.addAll(GraphValidator.validate(nodes));

        if (diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR)) {
            throw new PlatformException(Helpers.firstErrorCode(diagnostics), diagnostics);
        }
        return CapabilityGraph.build(nodes);
    }

    /**
     * Immutable staged edge referencing capabilities and ports by identity only.
     *
     * @param sourceId   the source capability id
     * @param targetId   the target capability id
     * @param outputPort the output port on the source
     * @param inputPort  the input port on the target
     */
    private record StagedEdge(String sourceId, String targetId, PortId outputPort, PortId inputPort) {
    }

    /**
     * Private nested helper that resolves providers and creates the initial
     * mutable nodes.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class NodeBuilder {

        private NodeBuilder() {
            // no instances
        }

        static void resolveProviders(ApplicationConfig application, ProviderResolver resolver,
                                     List<CapabilityNode> nodes, Map<String, CapabilityNode> nodeById,
                                     List<Diagnostic> diagnostics) {
            for (int i = 0; i < application.capabilities().size(); i++) {
                CapabilityConfig config = application.capabilities().get(i);
                if (nodeById.containsKey(config.id())) {
                    diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                            "/spec/capabilities/" + i + "/id", "duplicate capability id", "use a unique capability id"));
                    continue;
                }
                try {
                    ResolvedProvider resolved = resolver.resolve(config);
                    diagnostics.addAll(Helpers.rewritePaths(resolved.diagnostics(), i));
                    CapabilityNode node = CapabilityNode.mutable(config.id(), config.type(),
                            resolved.descriptor().providerId(), resolved.descriptor().version(),
                            resolved.factory(), resolved.descriptor(), config.config());
                    nodes.add(node);
                    nodeById.put(node.id(), node);
                } catch (PlatformException e) {
                    diagnostics.addAll(Helpers.rewritePaths(e.diagnostics(), i));
                }
            }
        }
    }

    /**
     * Private nested helper that stages the port bindings by immutable ids and
     * ports, validating source existence and both port names.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class EdgeStager {

        private EdgeStager() {
            // no instances
        }

        static List<StagedEdge> stage(ApplicationConfig application, Map<String, CapabilityNode> nodeById,
                                      List<Diagnostic> diagnostics) {
            List<StagedEdge> edges = new ArrayList<>();
            for (int i = 0; i < application.capabilities().size(); i++) {
                CapabilityConfig config = application.capabilities().get(i);
                CapabilityNode target = nodeById.get(config.id());
                if (target == null) {
                    continue;
                }
                for (int j = 0; j < config.inputs().size(); j++) {
                    InputBindingConfig input = config.inputs().get(j);
                    String path = "/spec/capabilities/" + i + "/inputs/" + j;
                    CapabilityNode source = nodeById.get(input.capability());
                    if (source == null) {
                        diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                path + "/capability", "referenced capability does not exist",
                                "reference an existing capability"));
                        continue;
                    }
                    PortId outputPort = Helpers.parsePortId(input.port());
                    if (outputPort == null || !source.outputPorts().containsKey(outputPort)) {
                        diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                path + "/port", "referenced output port does not exist",
                                "reference an existing output port"));
                        continue;
                    }
                    PortId inputPort = Helpers.parsePortId(input.inputPort());
                    if (inputPort == null || !target.inputPorts().containsKey(inputPort)) {
                        diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                path + "/port", "no matching input port on target capability",
                                "reference a valid input port"));
                        continue;
                    }
                    edges.add(new StagedEdge(input.capability(), config.id(), outputPort, inputPort));
                }
            }
            return edges;
        }
    }

    /**
     * Private nested helper that resolves the staged edges against the canonical
     * node map and freezes every node.
     *
     * <p>Nodes are processed in a deterministic topological order of the staged
     * edges so that a binding always references the canonical node of its source
     * and target. When the staged edges contain a cycle the nodes are processed
     * in declaration order; the graph build then reports the cycle.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class NodeFinalizer {

        private NodeFinalizer() {
            // no instances
        }

        static void finalizeNodes(Map<String, CapabilityNode> nodeById, List<StagedEdge> edges) {
            List<String> order = TopoOrder.compute(edges, nodeById.keySet());
            if (order == null) {
                order = new ArrayList<>(nodeById.keySet());
            }
            for (String id : order) {
                CapabilityNode target = nodeById.get(id);
                List<PortBinding> bindings = new ArrayList<>();
                for (StagedEdge edge : edges) {
                    if (edge.targetId().equals(id)) {
                        CapabilityNode source = nodeById.get(edge.sourceId());
                        bindings.add(new PortBinding(source, target, edge.outputPort(), edge.inputPort()));
                    }
                }
                target.setInputs(bindings);
            }
        }
    }

    /**
     * Private nested helper computing a deterministic topological order of the
     * staged edges with Kahn's algorithm.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class TopoOrder {

        private TopoOrder() {
            // no instances
        }

        static List<String> compute(List<StagedEdge> edges, Set<String> ids) {
            Map<String, Set<String>> dependencies = new LinkedHashMap<>();
            Map<String, Set<String>> consumers = new LinkedHashMap<>();
            for (String id : ids) {
                dependencies.put(id, new LinkedHashSet<>());
                consumers.put(id, new LinkedHashSet<>());
            }
            for (StagedEdge edge : edges) {
                dependencies.get(edge.targetId()).add(edge.sourceId());
                consumers.get(edge.sourceId()).add(edge.targetId());
            }
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            for (String id : ids) {
                inDegree.put(id, dependencies.get(id).size());
            }
            Deque<String> queue = new ArrayDeque<>();
            for (String id : ids) {
                if (inDegree.get(id) == 0) {
                    queue.add(id);
                }
            }
            List<String> order = new ArrayList<>();
            while (!queue.isEmpty()) {
                String id = queue.poll();
                order.add(id);
                for (String consumer : consumers.get(id)) {
                    int degree = inDegree.get(consumer) - 1;
                    inDegree.put(consumer, degree);
                    if (degree == 0) {
                        queue.add(consumer);
                    }
                }
            }
            return order.size() == ids.size() ? order : null;
        }
    }

    /**
     * Private nested helper with small shared utilities.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class Helpers {

        private Helpers() {
            // no instances
        }

        static List<Diagnostic> rewritePaths(List<Diagnostic> diagnostics, int index) {
            List<Diagnostic> result = new ArrayList<>();
            for (Diagnostic d : diagnostics) {
                String path = d.path() == null ? null : "/spec/capabilities/" + index + d.path();
                result.add(new Diagnostic(d.code(), d.severity(), path, d.message(), d.remediation()));
            }
            return result;
        }

        static PortId parsePortId(String value) {
            try {
                return new PortId(value);
            } catch (RuntimeException e) {
                return null;
            }
        }

        static PlatformErrorCode firstErrorCode(List<Diagnostic> diagnostics) {
            return diagnostics.stream()
                    .filter(d -> d.severity() == Severity.ERROR)
                    .findFirst()
                    .map(Diagnostic::code)
                    .orElse(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
        }
    }
}