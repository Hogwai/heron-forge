package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable collection of {@link CapabilityInput}s keyed by {@link PortId}.
 *
 * <p>Null keys or values, duplicate ports and incoherent bindings are rejected.
 * All views are immutable.
 */
public final class CapabilityInputs {

    private final Map<PortId, MaterializedDataSet> inputs;

    private CapabilityInputs(Map<PortId, MaterializedDataSet> inputs) {
        this.inputs = inputs;
    }

    /**
     * Creates capability inputs from a map of ports to data sets.
     *
     * @param inputs the map of ports to data sets
     * @return the capability inputs
     * @throws NullPointerException if {@code inputs} is {@code null}, or if it
     *                              contains a {@code null} key or value
     */
    public static CapabilityInputs of(Map<PortId, MaterializedDataSet> inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        return new CapabilityInputs(Map.copyOf(inputs));
    }

    /**
     * Creates capability inputs from a list of bindings.
     *
     * @param inputs the list of bindings
     * @return the capability inputs
     * @throws NullPointerException     if {@code inputs} is {@code null}, or if
     *                                  it contains a {@code null} element
     * @throws IllegalArgumentException if a port is bound more than once
     */
    public static CapabilityInputs of(List<CapabilityInput> inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        Map<PortId, MaterializedDataSet> map = new HashMap<>();
        for (CapabilityInput input : inputs) {
            Objects.requireNonNull(input, "inputs must not contain null");
            if (map.put(input.portId(), input.dataSet()) != null) {
                throw new IllegalArgumentException("duplicate port: " + input.portId());
            }
        }
        return new CapabilityInputs(Map.copyOf(map));
    }

    /**
     * Returns the data set bound to the given port, or {@code null} if absent.
     *
     * @param portId the port identifier
     * @return the data set, or {@code null} if absent
     * @throws NullPointerException if {@code portId} is {@code null}
     */
    public MaterializedDataSet get(PortId portId) {
        return inputs.get(Objects.requireNonNull(portId, "portId must not be null"));
    }

    /**
     * Returns whether the given port is bound.
     *
     * @param portId the port identifier
     * @return {@code true} if the port is bound
     * @throws NullPointerException if {@code portId} is {@code null}
     */
    public boolean contains(PortId portId) {
        return inputs.containsKey(Objects.requireNonNull(portId, "portId must not be null"));
    }

    /**
     * Returns the number of bound ports.
     *
     * @return the number of bound ports
     */
    public int size() {
        return inputs.size();
    }

    /**
     * Returns whether no port is bound.
     *
     * @return {@code true} if no port is bound
     */
    public boolean isEmpty() {
        return inputs.isEmpty();
    }

    /**
     * Returns an immutable view of the bound port identifiers.
     *
     * @return the bound port identifiers
     */
    public Set<PortId> portIds() {
        return inputs.keySet();
    }

    /**
     * Returns an immutable view of the ports to data sets.
     *
     * @return the ports to data sets
     */
    public Map<PortId, MaterializedDataSet> asMap() {
        return inputs;
    }
}
