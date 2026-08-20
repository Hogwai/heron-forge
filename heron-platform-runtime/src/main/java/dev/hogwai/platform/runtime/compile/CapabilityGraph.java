package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.Severity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable compiled capability graph.
 *
 * <p>A graph retains the nodes keyed by capability id, the dependency
 * (predecessor) and consumer (successor) adjacency in both directions, and an
 * immutable topological order computed with Kahn's algorithm. If the graph
 * contains a cycle, construction fails with a
 * {@link PlatformException} carrying {@link PlatformErrorCode#GRAPH_CYCLE_ERROR}
 * and the involved capability ids. All exposed collections are immutable.
 * Framework-independent.
 */
public final class CapabilityGraph {

    private final Map<String, CapabilityNode> nodes;
    private final Map<String, Set<String>> dependencies;
    private final Map<String, Set<String>> consumers;
    private final List<String> topologicalOrder;

    private CapabilityGraph(Map<String, CapabilityNode> nodes, Map<String, Set<String>> dependencies,
                            Map<String, Set<String>> consumers, List<String> topologicalOrder) {
        this.nodes = nodes;
        this.dependencies = dependencies;
        this.consumers = consumers;
        this.topologicalOrder = topologicalOrder;
    }

    /**
     * Builds an immutable graph from the given nodes.
     *
     * @param nodeList the ordered list of nodes
     * @return the immutable graph
     * @throws NullPointerException if {@code nodeList} is {@code null}
     * @throws PlatformException    with {@code GRAPH_CYCLE_ERROR} if the graph
     *                              contains a cycle
     */
    static CapabilityGraph build(List<CapabilityNode> nodeList) {
        Map<String, CapabilityNode> nodeMap = new LinkedHashMap<>();
        for (CapabilityNode node : nodeList) {
            nodeMap.put(node.id(), node);
        }

        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        Map<String, Set<String>> consumers = new LinkedHashMap<>();
        for (CapabilityNode node : nodeList) {
            dependencies.put(node.id(), new LinkedHashSet<>());
            consumers.put(node.id(), new LinkedHashSet<>());
        }
        for (CapabilityNode node : nodeList) {
            for (PortBinding binding : node.inputs()) {
                dependencies.get(node.id()).add(binding.source().id());
                consumers.get(binding.source().id()).add(node.id());
            }
        }

        List<String> order = TopoSort.sort(nodeList, dependencies, consumers);

        return new CapabilityGraph(
                Maps.immutableMap(nodeMap),
                Maps.immutableMapOfSets(dependencies),
                Maps.immutableMapOfSets(consumers),
                List.copyOf(order));
    }

    /**
     * Returns the node with the given capability id, if present.
     *
     * @param id the capability id
     * @return the node, or {@link Optional#empty()} if absent
     */
    public Optional<CapabilityNode> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    /**
     * Returns an immutable view of the capability ids.
     *
     * @return the capability ids
     */
    public Set<String> nodeIds() {
        return nodes.keySet();
    }

    /**
     * Returns an immutable view of the nodes in insertion order.
     *
     * @return the nodes
     */
    public List<CapabilityNode> nodes() {
        return List.copyOf(nodes.values());
    }

    /**
     * Returns the immutable set of dependencies (predecessors) of the given
     * capability, or an empty set if the capability is unknown.
     *
     * @param id the capability id
     * @return the dependency ids
     */
    public Set<String> dependencies(String id) {
        return dependencies.getOrDefault(id, Set.of());
    }

    /**
     * Returns the immutable set of consumers (successors) of the given
     * capability, or an empty set if the capability is unknown.
     *
     * @param id the capability id
     * @return the consumer ids
     */
    public Set<String> consumers(String id) {
        return consumers.getOrDefault(id, Set.of());
    }

    /**
     * Returns the immutable topological order of the capability ids.
     *
     * @return the topological order
     */
    public List<String> topologicalOrder() {
        return topologicalOrder;
    }

    /**
     * Returns the number of nodes in the graph.
     *
     * @return the number of nodes
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Private nested Kahn topological sort.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class TopoSort {

        private TopoSort() {
            // no instances
        }

        static List<String> sort(List<CapabilityNode> nodeList, Map<String, Set<String>> dependencies,
                                 Map<String, Set<String>> consumers) {
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            for (CapabilityNode node : nodeList) {
                inDegree.put(node.id(), dependencies.get(node.id()).size());
            }
            Deque<String> queue = new ArrayDeque<>();
            for (CapabilityNode node : nodeList) {
                if (inDegree.get(node.id()) == 0) {
                    queue.add(node.id());
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
            if (order.size() != nodeList.size()) {
                List<String> cycleIds = inDegree.entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
                throw new PlatformException(PlatformErrorCode.GRAPH_CYCLE_ERROR, List.of(
                        new Diagnostic(PlatformErrorCode.GRAPH_CYCLE_ERROR, Severity.ERROR, null,
                                "graph contains a cycle involving capabilities: " + String.join(", ", cycleIds),
                                "remove the cyclic dependency")));
            }
            return order;
        }
    }

    /**
     * Private nested immutable map helpers.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class Maps {

        private Maps() {
            // no instances
        }

        static <K, V> Map<K, V> immutableMap(Map<K, V> map) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }

        static Map<String, Set<String>> immutableMapOfSets(Map<String, Set<String>> map) {
            Map<String, Set<String>> result = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
                result.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
    }
}