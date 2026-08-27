package dev.hogwai.platform.spi.event;

/**
 * Contract for a pub/sub event bus.
 *
 * <p>Each implementation chooses its transport (Kafka, RabbitMQ, file system).
 * This SPI is separate from the worker invocation SPI.
 */
public interface EventBus {

    /**
     * Publishes an event to a topic.
     *
     * @param topic the topic name
     * @param event the event to publish
     */
    void publish(String topic, Event event);

    /**
     * Subscribes to a topic pattern.
     *
     * @param topicPattern the topic pattern (exact or wildcard)
     * @param handler      the event handler
     */
    void subscribe(String topicPattern, EventHandler handler);

    /**
     * Closes the bus and releases resources.
     */
    void close();
}
