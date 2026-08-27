package dev.hogwai.platform.spi.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable event publishable on a bus.
 *
 * @param eventId   the unique event identifier
 * @param topic     the source topic
 * @param type      the event type (e.g., "order.created")
 * @param payload   the event payload (key-value)
 * @param timestamp the creation timestamp
 * @param metadata  additional metadata (headers, etc.)
 */
public record Event(
        String eventId,
        String topic,
        String type,
        Map<String, Object> payload,
        Instant timestamp,
        Map<String, String> metadata
) {

    /**
     * Validates the required fields.
     */
    public Event {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        Objects.requireNonNull(topic, "topic must not be null");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
    }

    /**
     * Creates an event with auto-generated eventId and timestamp.
     *
     * @param topic   the topic name
     * @param type    the event type
     * @param payload the event payload
     * @return the new event
     */
    public static Event of(String topic, String type, Map<String, Object> payload) {
        return new Event(
                UUID.randomUUID().toString(),
                topic,
                type,
                payload,
                Instant.now(),
                Map.of()
        );
    }
}
