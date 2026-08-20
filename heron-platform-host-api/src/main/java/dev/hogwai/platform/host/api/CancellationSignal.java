package dev.hogwai.platform.host.api;

/** Signals whether cancellation has been requested for an invocation. */
@FunctionalInterface
public interface CancellationSignal {

    /**
     * Returns whether cancellation has been requested.
     *
     * @return whether cancellation was requested
     */
    boolean isCancellationRequested();
}
