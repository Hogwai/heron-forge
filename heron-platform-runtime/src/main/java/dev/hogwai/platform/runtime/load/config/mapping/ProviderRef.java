package dev.hogwai.platform.runtime.load.config.mapping;

/**
 * Small holder for a validated provider id and version.
 *
 * <p>Package-private value returned by {@link ProviderMapper} to the capability
 * mapper so a validated provider pair can cross the package boundary without
 * leaking a mutable intermediate type.
 */
final class ProviderRef {

    private final String id;
    private final String version;

    /**
     * Creates a provider reference.
     *
     * @param id      the provider id
     * @param version the provider version
     */
    ProviderRef(String id, String version) {
        this.id = id;
        this.version = version;
    }

    /**
     * @return the provider id
     */
    String id() {
        return id;
    }

    /**
     * @return the provider version
     */
    String version() {
        return version;
    }
}
