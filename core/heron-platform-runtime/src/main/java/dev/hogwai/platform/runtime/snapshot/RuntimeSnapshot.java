package dev.hogwai.platform.runtime.snapshot;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Immutable snapshot of a compiled generation.
 *
 * <p>A snapshot binds a non-blank generation identifier to the compiled
 * {@link CapabilityGraph} and capability instance factories keyed by
 * capability id. Each call to {@link #instance(String)} creates a fresh
 * {@link CapabilityInstance} via the corresponding factory, ensuring that
 * concurrent requests never share mutable state.
 *
 * <p>All collections are defensively copied and exposed immutably; no mutable
 * collection is ever exposed. The factory keys must cover exactly the graph
 * node ids: a missing or extra factory is rejected at construction with an
 * {@link IllegalArgumentException}.
 */
public record RuntimeSnapshot(String generationId, CapabilityGraph graph,
                              Map<String, Supplier<CapabilityInstance>> instanceFactories) {

    public RuntimeSnapshot {
        Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) {
            throw new IllegalArgumentException("generationId must not be blank");
        }
        Objects.requireNonNull(graph, "graph must not be null");
        Map<String, Supplier<CapabilityInstance>> copy =
                Map.copyOf(Objects.requireNonNull(instanceFactories, "instanceFactories must not be null"));
        for (String key : copy.keySet()) {
            if (key.isBlank()) {
                throw new IllegalArgumentException("instance factory keys must not be blank");
            }
        }
        if (!copy.keySet().equals(graph.nodeIds())) {
            throw new IllegalArgumentException("instance factories must cover exactly the graph node ids");
        }
        instanceFactories = copy;
    }

    /**
     * Creates a capability instance for the given capability id.
     *
     * @param capabilityId the capability id
     * @return a new instance, or {@link Optional#empty()} if no factory is registered
     */
    public Optional<CapabilityInstance> instance(String capabilityId) {
        Supplier<CapabilityInstance> factory = instanceFactories.get(capabilityId);
        return Optional.ofNullable(factory).map(Supplier::get);
    }
}