package dev.hogwai.platform.runtime.snapshot;

import dev.hogwai.platform.runtime.graph.CapabilityGraph;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of a compiled generation.
 *
 * <p>A snapshot binds a non-blank generation identifier to the compiled
 * {@link CapabilityGraph} and the capability instances created for it, keyed by
 * capability id. Construction is package-private and receives already compiled
 * graph data and created instances. This class neither resolves providers nor
 * executes capabilities.
 *
 * <p>All collections are defensively copied and exposed immutably; no mutable
 * collection is ever exposed. The instance keys must cover exactly the graph
 * node ids: a missing or extra instance is rejected at construction with an
 * {@link IllegalArgumentException}. The class is immutable and
 * framework-independent.
 */
public final class RuntimeSnapshot {

    private final String generationId;
    private final CapabilityGraph graph;
    private final Map<String, CapabilityInstance> instances;

    RuntimeSnapshot(String generationId, CapabilityGraph graph, Map<String, CapabilityInstance> instances) {
        Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) {
            throw new IllegalArgumentException("generationId must not be blank");
        }
        this.generationId = generationId;
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        Map<String, CapabilityInstance> copy =
                Map.copyOf(Objects.requireNonNull(instances, "instances must not be null"));
        for (String key : copy.keySet()) {
            if (key.isBlank()) {
                throw new IllegalArgumentException("instance keys must not be blank");
            }
        }
        if (!copy.keySet().equals(graph.nodeIds())) {
            throw new IllegalArgumentException("instances must cover exactly the graph node ids");
        }
        this.instances = copy;
    }

    /**
     * Returns the non-blank generation identifier.
     *
     * @return the generation identifier
     */
    public String generationId() {
        return generationId;
    }

    /**
     * Returns the compiled capability graph.
     *
     * @return the immutable graph
     */
    public CapabilityGraph graph() {
        return graph;
    }

    /**
     * Returns an immutable view of the capability instances keyed by capability
     * id.
     *
     * @return the immutable instances
     */
    public Map<String, CapabilityInstance> instances() {
        return instances;
    }

    /**
     * Returns the capability instance for the given capability id, if present.
     *
     * @param capabilityId the capability id
     * @return the instance, or {@link Optional#empty()} if absent
     */
    public Optional<CapabilityInstance> instance(String capabilityId) {
        return Optional.ofNullable(instances.get(capabilityId));
    }
}
