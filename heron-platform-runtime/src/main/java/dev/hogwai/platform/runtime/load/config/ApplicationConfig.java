package dev.hogwai.platform.runtime.load.config;

import dev.hogwai.platform.runtime.load.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.load.config.entrypoint.EntrypointConfig;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, framework-independent model of an application configuration.
 *
 * <p>Holds the root {@code apiVersion}, {@code kind}, the application
 * {@code name} from {@code metadata}, and the lists of {@link CapabilityConfig}s
 * and {@link EntrypointConfig}s from {@code spec}. All collections are
 * defensively copied.
 */
public final class ApplicationConfig {

    private final String apiVersion;
    private final String kind;
    private final String name;
    private final List<CapabilityConfig> capabilities;
    private final List<EntrypointConfig> entrypoints;

    /**
     * Creates an application configuration.
     *
     * @param apiVersion    the API version
     * @param kind          the kind
     * @param name          the application name
     * @param capabilities  the capabilities
     * @throws NullPointerException if any argument is {@code null}
     */
    public ApplicationConfig(String apiVersion, String kind, String name, List<CapabilityConfig> capabilities) {
        this(apiVersion, kind, name, capabilities, List.of());
    }

    /**
     * Creates an application configuration with its entrypoints.
     *
     * @param apiVersion    the API version
     * @param kind          the kind
     * @param name          the application name
     * @param capabilities  the capabilities
     * @param entrypoints   the entrypoints
     * @throws NullPointerException if any argument is {@code null}
     */
    public ApplicationConfig(String apiVersion, String kind, String name,
                             List<CapabilityConfig> capabilities, List<EntrypointConfig> entrypoints) {
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
        this.entrypoints = List.copyOf(Objects.requireNonNull(entrypoints, "entrypoints must not be null"));
    }

    /**
     * @return the API version
     */
    public String apiVersion() {
        return apiVersion;
    }

    /**
     * @return the kind
     */
    public String kind() {
        return kind;
    }

    /**
     * @return the application name
     */
    public String name() {
        return name;
    }

    /**
     * @return an immutable view of the capabilities
     */
    public List<CapabilityConfig> capabilities() {
        return capabilities;
    }

    /**
     * @return an immutable view of the entrypoints
     */
    public List<EntrypointConfig> entrypoints() {
        return entrypoints;
    }
}
