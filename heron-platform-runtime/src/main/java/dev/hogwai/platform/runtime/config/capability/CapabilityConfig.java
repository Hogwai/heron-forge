package dev.hogwai.platform.runtime.config.capability;

import dev.hogwai.platform.runtime.config.input.InputBindingConfig;
import dev.hogwai.platform.runtime.config.safe.SafeConfig;
import dev.hogwai.platform.spi.CapabilityKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, framework-independent model of a single capability declaration.
 *
 * <p>Carries the capability {@code id}, {@code type} (as a
 * {@link CapabilityKind}), the provider {@code id} and {@code version}, an
 * immutable map of safe configuration values and the list of
 * {@link InputBindingConfig}s. The configuration map is validated and
 * deep-copied recursively; only v1 scalar types, lists and maps with
 * {@link String} keys are accepted. No provider-specific Java types are stored.
 */
public final class CapabilityConfig {

    private final String id;
    private final CapabilityKind type;
    private final String providerId;
    private final String providerVersion;
    private final Map<String, Object> config;
    private final List<InputBindingConfig> inputs;

    /**
     * Creates a capability configuration.
     *
     * @param id              the capability id
     * @param type            the capability type
     * @param providerId      the provider id
     * @param providerVersion the provider version
     * @param config          the safe configuration values
     * @param inputs          the input bindings
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code config} contains a non-String
     *                                  key or an unsupported value type
     */
    public CapabilityConfig(String id, CapabilityKind type, String providerId, String providerVersion,
                            Map<String, Object> config, List<InputBindingConfig> inputs) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion must not be null");
        this.config = SafeConfig.copy(config);
        this.inputs = List.copyOf(inputs);
    }

    /**
     * @return the capability id
     */
    public String id() {
        return id;
    }

    /**
     * @return the capability type
     */
    public CapabilityKind type() {
        return type;
    }

    /**
     * @return the provider id
     */
    public String providerId() {
        return providerId;
    }

    /**
     * @return the provider version
     */
    public String providerVersion() {
        return providerVersion;
    }

    /**
     * @return an immutable view of the safe configuration values
     */
    public Map<String, Object> config() {
        return config;
    }

    /**
     * @return an immutable view of the input bindings
     */
    public List<InputBindingConfig> inputs() {
        return inputs;
    }
}
