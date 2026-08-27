package dev.hogwai.platform.spi.registry;

import java.util.List;
import java.util.Optional;

/**
 * Persistent store of sealed application generations.
 *
 * <p>A store archives generation definitions, never executable state. Records
 * are keyed by the pair {@code (applicationId, generationId)}; saving a record
 * with an already-known key replaces the previous entry (idempotent upsert).
 *
 * <p>Lifecycle is enforced by {@link #transition}: statuses move strictly
 * forward along {@code EXPERIMENTAL -> STABLE -> DEPRECATED -> RETIRED}.
 * Implementations must reject any backward transition, any
 * transition to the current status, and any transition on an unknown record.
 *
 * <p>Implementations own their resources and must be closed explicitly via
 * {@link #close()}; {@code close()} is expected to be idempotent.
 */
public interface GenerationStore extends AutoCloseable {

    /**
     * Persists a generation generationRecord, keyed by {@code (applicationId, generationId)}.
     *
     * <p>This operation is idempotent: saving a generationRecord whose key already exists
     * replaces the stored entry; re-writing an identical generationRecord leaves the store
     * unchanged. Implementations must persist the generationRecord atomically from the
     * caller's point of view.
     *
     * @param generationRecord the generation generationRecord to persist
     */
    void save(GenerationRecord generationRecord);

    /**
     * Returns the stored record for the given pair, if any.
     *
     * @param applicationId identifier of the application
     * @param generationId  identifier of the generation within the application
     * @return the stored record, or an empty optional when unknown
     */
    Optional<GenerationRecord> find(String applicationId, String generationId);

    /**
     * Returns all records of an application, most recent first.
     *
     * <p>Ordering is deterministic: records are sorted by descending
     * {@link GenerationRecord#createdAt()}, ties broken by ascending
     * {@link GenerationRecord#generationId()} in natural string order.
     *
     * @param applicationId identifier of the application
     * @return the application's records in deterministic order; never {@code null},
     * empty when the application has no generations
     */
    List<GenerationRecord> history(String applicationId);

    /**
     * Moves a stored record forward to {@code target}, enforcing the monotone
     * lifecycle {@code EXPERIMENTAL -> STABLE -> DEPRECATED -> RETIRED}.
     *
     * <p>The transition succeeds if and only if the record exists and
     * {@code target} is strictly later in the lifecycle than the current status.
     * It returns {@code false} when the record does not exist, when
     * {@code target} equals the current status, when it precedes the current
     * status, or when the record is already {@link GenerationStatus#RETIRED}
     * (terminal). On success the stored record keeps every other component
     * unchanged and only its status is updated.
     *
     * @param applicationId identifier of the application
     * @param generationId  identifier of the generation within the application
     * @param target        the requested target status
     * @return {@code true} when the transition was applied, {@code false} otherwise
     */
    boolean transition(String applicationId, String generationId, GenerationStatus target);

    /**
     * Releases resources owned by this store.
     *
     * <p>Implementations are expected to make this call idempotent: closing an
     * already-closed store must not fail.
     */
    @Override
    void close();
}
