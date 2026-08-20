package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.runtime.config.capability.CapabilityConfig;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, framework-independent model of an application configuration.
 *
 * <p>Holds the root {@code apiVersion}, {@code kind}, the application
 * {@code name} from {@code metadata} and the list of {@link CapabilityConfig}s
 * from {@code spec.capabilities}. All collections are defensively copied.
 */
public final class ApplicationConfig {

    private final String apiVersion;
    private final String kind;
    private final String name;
    private final List<CapabilityConfig> capabilities;

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
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.capabilities = List.copyOf(capabilities);
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
}
