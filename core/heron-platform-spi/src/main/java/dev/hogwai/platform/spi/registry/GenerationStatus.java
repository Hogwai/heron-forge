package dev.hogwai.platform.spi.registry;

/**
 * Lifecycle status of a stored generation.
 *
 * <p>Statuses form a strictly monotone lifecycle:
 * {@code EXPERIMENTAL -> STABLE -> DEPRECATED -> RETIRED}. Status
 * transitions may only move forward in this order; {@link #RETIRED} is
 * terminal. See {@link GenerationStore#transition} for the exact contract.
 */
public enum GenerationStatus {
    /**
     * A generation registered for evaluation; not yet promoted.
     */
    EXPERIMENTAL,
    /**
     * A generation promoted as the reference for its application.
     */
    STABLE,
    /**
     * A stable generation superseded by a newer one and scheduled for retirement.
     */
    DEPRECATED,
    /**
     * A terminal status: the generation is archived and must not be activated.
     */
    RETIRED
}
