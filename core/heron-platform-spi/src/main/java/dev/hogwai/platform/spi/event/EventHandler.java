package dev.hogwai.platform.spi.event;

/**
 * Handler for received events.
 *
 * <p>Implementations must be non-blocking.
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Called when an event is received.
     *
     * @param event the received event
     */
    void onEvent(Event event);
}
