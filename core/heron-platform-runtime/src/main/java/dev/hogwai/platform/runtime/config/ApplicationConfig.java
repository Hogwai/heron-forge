package dev.hogwai.platform.runtime.config;


import java.util.List;
import java.util.Objects;

/**
 * Immutable, framework-independent model of an application configuration.
 *
 * <p>Holds the root {@code apiVersion}, normalized application name, and the
 * lists of {@link CapabilityConfig}s and {@link EntrypointConfig}s. All collections are
 * defensively copied.
 */
public record ApplicationConfig(String apiVersion,
                                String name,
                                List<CapabilityConfig> capabilities,
                                List<EntrypointConfig> entrypoints) {

    /**
     * Creates an application configuration.
     *
     * @param apiVersion   the API version
     * @param name         the application name
     * @param capabilities the capabilities
     * @throws NullPointerException if any argument is {@code null}
     */
    public ApplicationConfig(String apiVersion, String name, List<CapabilityConfig> capabilities) {
        this(apiVersion, name, capabilities, List.of());
    }

    /**
     * Creates an application configuration with its entrypoints.
     *
     * @param apiVersion   the API version
     * @param name         the application name
     * @param capabilities the capabilities
     * @param entrypoints  the entrypoints
     * @throws NullPointerException if any argument is {@code null}
     */
    public ApplicationConfig(String apiVersion, String name,
                             List<CapabilityConfig> capabilities, List<EntrypointConfig> entrypoints) {
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
        this.entrypoints = List.copyOf(Objects.requireNonNull(entrypoints, "entrypoints must not be null"));
    }

    /**
     * @return the API version
     */
    @Override
    public String apiVersion() {
        return apiVersion;
    }

    /**
     * @return the application name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * @return an immutable view of the capabilities
     */
    @Override
    public List<CapabilityConfig> capabilities() {
        return capabilities;
    }

    /**
     * @return an immutable view of the entrypoints
     */
    @Override
    public List<EntrypointConfig> entrypoints() {
        return entrypoints;
    }
}
