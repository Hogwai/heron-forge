package dev.hogwai.platform.runtime.registry;

import dev.hogwai.platform.spi.registry.GenerationRecord;
import java.util.Objects;

/**
 * Outcome of one {@link RegistryService#register} call.
 *
 * <p>Carries the stored {@link GenerationRecord} together with the creation
 * flag so callers can tell a fresh registration from an idempotent replay
 * without re-computing the content digest themselves:
 * {@code created} is {@code true} when the generationRecord was newly persisted by this
 * call, and {@code false} when an already-known identical generationRecord was returned
 * unchanged.
 *
 * @param generationRecord  the stored generation generationRecord, never {@code null}
 * @param created {@code true} when the generationRecord was persisted by this call,
 *                {@code false} when the pre-existing generationRecord was returned as is
 */
public record RegistrationResult(GenerationRecord generationRecord, boolean created) {

    /**
     * Creates a registration result.
     *
     * @throws NullPointerException if {@code generationRecord} is {@code null}
     */
    public RegistrationResult {
        Objects.requireNonNull(generationRecord, "generationRecord must not be null");
    }
}
