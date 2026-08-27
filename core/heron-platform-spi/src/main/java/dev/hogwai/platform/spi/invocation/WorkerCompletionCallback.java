package dev.hogwai.platform.spi.invocation;

import dev.hogwai.platform.spi.host.ExecutionOutcome;

/**
 * Callback for asynchronous worker invocation results.
 *
 * <p>Implementations must be non-blocking. The callback is called
 * when the worker completes successfully or fails.
 */
public interface WorkerCompletionCallback {

    /**
     * Called when the worker completes successfully.
     *
     * @param outcome the execution outcome
     */
    void onComplete(ExecutionOutcome outcome);

    /**
     * Called when the worker fails.
     *
     * <p>Default implementation propagates the error as a RuntimeException.
     *
     * @param error the failure cause
     */
    default void onFailure(Throwable error) {
        throw new RuntimeException(error);
    }
}
