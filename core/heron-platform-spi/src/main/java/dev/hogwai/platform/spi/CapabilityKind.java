package dev.hogwai.platform.spi;

/**
 * Kind of a capability exposed by a provider.
 *
 */
public enum CapabilityKind {
    /** A capability that produces data. */
    SOURCE,
    /** A capability that transforms data. */
    TRANSFORM
}
