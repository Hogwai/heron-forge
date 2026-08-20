package dev.hogwai.platform.runtime.load.config.input;

import java.util.Objects;

/**
 * Immutable, framework-independent model of a single input binding.
 *
 * <p>An input binding explicitly names the target input port on the current
 * capability ({@code inputPort}) and references another capability by
 * {@code id} and one of its output ports by {@code port}. The target input
 * port name is preserved explicitly; no heuristic is used to infer it.
 * Framework-independent and immutable.
 *
 * @param inputPort  the input port name on the current capability
 * @param capability the referenced capability id
 * @param port       the referenced output port on the source capability
 */
public record InputBindingConfig(String inputPort, String capability, String port) {

    /**
     * Compact constructor rejecting {@code null} arguments.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public InputBindingConfig {
        Objects.requireNonNull(inputPort, "inputPort must not be null");
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(port, "port must not be null");
    }
}