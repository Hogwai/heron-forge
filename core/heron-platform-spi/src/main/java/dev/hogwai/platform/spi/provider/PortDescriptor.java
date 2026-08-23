package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.Schema;
import java.util.Objects;

/**
 * Immutable description of a single data port exposed by a capability.
 *
 * <p>A port carries a {@link PortId}, the {@link Schema} of the data flowing
 * through it and a {@code required} flag. All components are non-null and the
 * descriptor is immutable. Framework-independent.
 */
public record PortDescriptor(PortId portId, Schema schema, boolean required) {

    /**
     * Creates a port descriptor.
     *
     * @param portId   the port identifier
     * @param schema   the schema of the data flowing through the port
     * @param required whether the port is required
     * @throws NullPointerException if {@code portId} or {@code schema} is {@code null}
     */
    public PortDescriptor(PortId portId, Schema schema, boolean required) {
        this.portId = Objects.requireNonNull(portId, "portId must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.required = required;
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
     * Returns the schema of the data flowing through the port.
     *
     * @return the schema of the data flowing through the port
     */
    @Override
    public Schema schema() {
        return schema;
    }

    /**
     * Returns whether the port is required.
     *
     * @return whether the port is required
     */
    @Override
    public boolean required() {
        return required;
    }
}
