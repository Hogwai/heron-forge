package dev.hogwai.platform.spi.observation;

import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable platform event describing a lifecycle or capability occurrence.
 *
 * <p>An event exposes only identifiers, the provider version, the capability
 * identifier, a {@link EventStatus}, a duration, optional dataset size metadata
 * and an optional failure code. It never carries records, raw configuration or
 * secrets. The schema version is fixed at {@value #SCHEMA_VERSION}.
 * Framework-independent and immutable.
 *
 * @param schemaVersion    the event schema version
 * @param type             the event type
 * @param providerId       the provider identifier
 * @param providerVersion  the provider version
 * @param capabilityId     the capability identifier
 * @param snapshotId       the snapshot identifier
 * @param requestId        the request identifier
 * @param status           the event status
 * @param duration         the non-negative duration
 * @param datasetRowCount  the optional dataset row count, or {@code null}
 * @param datasetSizeBytes the optional dataset size in bytes, or {@code null}
 * @param failureCode      the optional failure code, or {@code null}
 */
public record PlatformEvent(
        int schemaVersion,
        PlatformEventType type,
        ProviderId providerId,
        ProviderVersion providerVersion,
        String capabilityId,
        String snapshotId,
        String requestId,
        EventStatus status,
        Duration duration,
        Long datasetRowCount,
        Long datasetSizeBytes,
        String failureCode) {

    /** The supported event schema version. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Status of a platform event.
     */
    public enum EventStatus {
        /** The operation started. */
        STARTED,
        /** The operation completed successfully. */
        COMPLETED,
        /** The operation failed. */
        FAILED
    }

    /**
     * Compact constructor enforcing the event contract.
     *
     * @throws NullPointerException     if {@code type}, {@code providerId},
     *                                  {@code providerVersion}, {@code status} or
     *                                  {@code duration} is {@code null}, or if an
     *                                  identifier is {@code null}
     * @throws IllegalArgumentException if {@code schemaVersion} is not
     *                                  {@value #SCHEMA_VERSION}, if an identifier
     *                                  is blank, if {@code duration} is negative,
     *                                  if a dataset size is negative, or if
     *                                  {@code failureCode} is blank
     */
    public PlatformEvent {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schemaVersion: " + schemaVersion);
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(providerVersion, "providerVersion must not be null");
        requireNonBlank(capabilityId, "capabilityId");
        requireNonBlank(snapshotId, "snapshotId");
        requireNonBlank(requestId, "requestId");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (datasetRowCount != null && datasetRowCount < 0) {
            throw new IllegalArgumentException("datasetRowCount must not be negative");
        }
        if (datasetSizeBytes != null && datasetSizeBytes < 0) {
            throw new IllegalArgumentException("datasetSizeBytes must not be negative");
        }
        if (failureCode != null && failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode must not be blank");
        }
    }

    private static void requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
