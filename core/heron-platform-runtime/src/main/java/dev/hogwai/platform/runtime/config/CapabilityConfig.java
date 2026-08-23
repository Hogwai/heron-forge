package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.runtime.config.safe.SafeConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable model of a single capability declaration.
 *
 * <p>Carries the capability {@code id}, the provider {@code id} and
 * {@code version}, an immutable map of safe configuration values and the list of
 * {@link InputBindingConfig}s. The configuration map is validated and
 * deep-copied recursively; only v1 scalar types, lists and maps with
 * {@link String} keys are accepted. No provider-specific Java types are stored.
 */
public record CapabilityConfig(String id, String providerId, String providerVersion,
                               Map<String, Object> config, List<InputBindingConfig> inputs) {

    /**
     * Creates a capability configuration.
     *
     * @param id              the capability id
     * @param providerId      the provider id
     * @param providerVersion the provider version
     * @param config          the safe configuration values
     * @param inputs          the input bindings
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code config} contains a non-String
     *                                  key or an unsupported value type
     */
    public CapabilityConfig {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(providerVersion, "providerVersion must not be null");
        config = SafeConfig.copy(config);
        inputs = List.copyOf(inputs);
    }
}