package dev.hogwai.platform.runtime.snapshot;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
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
public record RuntimeSnapshot(String generationId, CapabilityGraph graph,
                              Map<String, CapabilityInstance> instances) {

    public RuntimeSnapshot {
        Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) {
            throw new IllegalArgumentException("generationId must not be blank");
        }
        Objects.requireNonNull(graph, "graph must not be null");
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
        instances = copy;
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