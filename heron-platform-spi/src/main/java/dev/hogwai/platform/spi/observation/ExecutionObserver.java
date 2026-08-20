package dev.hogwai.platform.spi.observation;

/**
 * Receives {@link PlatformEvent}s without any return channel.
 *
 * <p>Observers never receive records or raw configuration by contract.
 * Framework-independent.
 */
@FunctionalInterface
public interface ExecutionObserver {

    /**
     * Handles a platform event.
     *
     * @param event the event
     */
    void onEvent(PlatformEvent event);
}
