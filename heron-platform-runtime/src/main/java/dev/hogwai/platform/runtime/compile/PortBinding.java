package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import java.util.Objects;

/**
 * Immutable binding of a target capability input port to a source capability
 * output port.
 *
 * <p>A binding retains the source and target {@link CapabilityNode}s together
 * with the named output port on the source and the named input port on the
 * target, so the full edge is preserved without relying on ids alone.
 * Framework-independent.
 */
public final class PortBinding {

    private final CapabilityNode source;
    private final CapabilityNode target;
    private final PortId outputPort;
    private final PortId inputPort;

    /**
     * Creates a port binding.
     *
     * @param source     the source capability node
     * @param target     the target capability node
     * @param outputPort the output port on the source
     * @param inputPort  the input port on the target
     * @throws NullPointerException if any argument is {@code null}
     */
    PortBinding(CapabilityNode source, CapabilityNode target, PortId outputPort, PortId inputPort) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.outputPort = Objects.requireNonNull(outputPort, "outputPort must not be null");
        this.inputPort = Objects.requireNonNull(inputPort, "inputPort must not be null");
    }

    /**
     * Returns the source capability node.
     *
     * @return the source capability node
     */
    public CapabilityNode source() {
        return source;
    }

    /**
     * Returns the target capability node.
     *
     * @return the target capability node
     */
    public CapabilityNode target() {
        return target;
    }

    /**
     * Returns the output port on the source.
     *
     * @return the output port
     */
    public PortId outputPort() {
        return outputPort;
    }

    /**
     * Returns the input port on the target.
     *
     * @return the input port
     */
    public PortId inputPort() {
        return inputPort;
    }

    /**
     * Returns the descriptor of the output port on the source.
     *
     * @return the output port descriptor
     */
    public PortDescriptor outputPortDescriptor() {
        return source.outputPorts().get(outputPort);
    }

    /**
     * Returns the descriptor of the input port on the target.
     *
     * @return the input port descriptor
     */
    public PortDescriptor inputPortDescriptor() {
        return target.inputPorts().get(inputPort);
    }
}