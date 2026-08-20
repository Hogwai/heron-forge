package dev.hogwai.platform.spi.observation;

/**
 * Type of a {@link PlatformEvent}.
 *
 * <p>Framework-independent and immutable.
 */
public enum PlatformEventType {
    /** A snapshot was built. */
    SNAPSHOT_BUILT,
    /** A snapshot was activated. */
    SNAPSHOT_ACTIVATED,
    /** A reload was rejected. */
    RELOAD_REJECTED,
    /** A snapshot was retired. */
    SNAPSHOT_RETIRED,
    /** A capability started executing. */
    CAPABILITY_STARTED,
    /** A capability completed executing. */
    CAPABILITY_COMPLETED,
    /** A capability failed during execution. */
    CAPABILITY_FAILED
}
