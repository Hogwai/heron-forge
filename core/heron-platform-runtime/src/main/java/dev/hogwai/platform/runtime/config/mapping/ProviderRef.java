package dev.hogwai.platform.runtime.config.mapping;

/**
 * Small holder for a validated provider id and version.
 *
 * <p>Package-private value returned by {@link ProviderMapper} to the capability
 * mapper so a validated provider pair can cross the package boundary without
 * leaking a mutable intermediate type.
 */
record ProviderRef(String id, String version) {
}