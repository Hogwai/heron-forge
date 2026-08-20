package dev.hogwai.platform.runtime.load;

import java.util.Objects;

/**
 * Immutable lifecycle candidate produced by {@link SnapshotBuilder}.
 *
 * <p>A candidate owns the immutable {@link RuntimeSnapshot} and the runtime
 * {@link ResourceTracker} that owns its capability instances. Closing the
 * candidate releases those instances through the tracker.
 */
final class SnapshotCandidate implements AutoCloseable {

    private final RuntimeSnapshot snapshot;
    private final ResourceTracker tracker;

    /**
     * Creates a candidate.
     *
     * @param snapshot   the immutable snapshot
     * @param tracker    the resource tracker owning the instances
     * @throws NullPointerException if any argument is {@code null}
     */
    SnapshotCandidate(RuntimeSnapshot snapshot, ResourceTracker tracker) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
    }

    /**
     * Returns the immutable snapshot.
     *
     * @return the snapshot
     */
    RuntimeSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Releases all capability instances owned by this candidate.
     *
     * <p>The underlying tracker makes this operation idempotent and attempts
     * every registered close even when one of them fails.
     */
    @Override
    public void close() {
        tracker.close();
    }
}
