package dev.hogwai.platform.spi;

/**
 * Kind of a capability exposed by a provider.
 *
 * <p>Framework-independent and immutable.
 */
public enum CapabilityKind {
    /** A capability that produces data. */
    SOURCE,
    /** A capability that transforms data. */
    TRANSFORM
}
