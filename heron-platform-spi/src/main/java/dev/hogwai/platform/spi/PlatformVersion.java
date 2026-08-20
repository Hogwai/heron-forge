package dev.hogwai.platform.spi;

/**
 * Version constants for the Heron Forge platform.
 *
 * <p>This is a framework-independent, immutable holder of platform-level version
 * identifiers. It carries no state and cannot be instantiated.
 */
public final class PlatformVersion {

    /** Canonical identifier of the platform configuration API. */
    public static final String CONFIG_API = "platform.dev/v1alpha1";

    private PlatformVersion() {
        // no instances
    }
}
