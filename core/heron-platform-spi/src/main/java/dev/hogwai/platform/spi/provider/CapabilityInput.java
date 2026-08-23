package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import java.util.Objects;

/**
 * Immutable binding of a single named port to a {@link MaterializedDataSet}.
 *
 */
public record CapabilityInput(PortId portId, MaterializedDataSet dataSet) {

    /**
     * Creates a capability input.
     *
     * @param portId  the port identifier
     * @param dataSet the data set bound to the port
     * @throws NullPointerException if {@code portId} or {@code dataSet} is {@code null}
     */
    public CapabilityInput(PortId portId, MaterializedDataSet dataSet) {
        this.portId = Objects.requireNonNull(portId, "portId must not be null");
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet must not be null");
    }

    /**
     * Returns the port identifier.
     *
     * @return the port identifier
     */
    @Override
    public PortId portId() {
        return portId;
    }

    /**
     * Returns the data set bound to the port.
     *
     * @return the data set bound to the port
     */
    @Override
    public MaterializedDataSet dataSet() {
        return dataSet;
    }
}
