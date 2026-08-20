package dev.hogwai.platform.spi.provider;

import java.time.Clock;
import java.util.Objects;

/**
 * Immutable context passed to {@link ProviderFactory#create}.
 *
 * <p>It exposes only the application identity, the snapshot identifier, a
 * {@link Clock} and a {@link ResourceTracker}. It carries no logger, metrics,
 * event sink, secrets or business payload. Framework-independent.
 */
@SuppressWarnings("java:S6206")
public final class BuildContext {

    private final String applicationId;
    private final String snapshotId;
    private final Clock clock;
    private final ResourceTracker resourceTracker;

    /**
     * Creates a build context.
     *
     * @param applicationId  the non-blank application identity
     * @param snapshotId     the non-blank snapshot identifier
     * @param clock          the clock
     * @param resourceTracker the resource tracker
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code applicationId} or {@code snapshotId} is blank
     */
    public BuildContext(String applicationId, String snapshotId, Clock clock, ResourceTracker resourceTracker) {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        if (applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId must not be blank");
        }
        this.applicationId = applicationId;
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        if (snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be blank");
        }
        this.snapshotId = snapshotId;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.resourceTracker = Objects.requireNonNull(resourceTracker, "resourceTracker must not be null");
    }

    /**
     * Returns the application identity.
     *
     * @return the application identity
     */
    public String applicationId() {
        return applicationId;
    }

    /**
     * Returns the snapshot identifier.
     *
     * @return the snapshot identifier
     */
    public String snapshotId() {
        return snapshotId;
    }

    /**
     * Returns the clock.
     *
     * @return the clock
     */
    public Clock clock() {
        return clock;
    }

    /**
     * Returns the resource tracker.
     *
     * @return the resource tracker
     */
    public ResourceTracker resourceTracker() {
        return resourceTracker;
    }
}
