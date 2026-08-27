package dev.hogwai.platform.runtime.invocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;

/**
 * Handler for worker completion callbacks.
 */
public class WorkerCompletionHandler {

    private final Map<String, List<WorkerCompletionCallback>> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a callback for a worker.
     *
     * @param workerId the worker identifier
     * @param callback the completion callback
     */
    public void register(String workerId, WorkerCompletionCallback callback) {
        handlers.computeIfAbsent(workerId, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    /**
     * Dispatches the outcome to all registered callbacks for a worker.
     *
     * <p>Each callback is dispatched on a separate Virtual Thread.
     *
     * @param workerId the worker identifier
     * @param outcome  the execution outcome
     */
    public void dispatch(String workerId, ExecutionOutcome outcome) {
        handlers.getOrDefault(workerId, List.of()).forEach(cb ->
                Thread.startVirtualThread(() -> cb.onComplete(outcome))
        );
    }

    /**
     * Removes all callbacks for a worker.
     *
     * @param workerId the worker identifier
     */
    public void clear(String workerId) {
        handlers.remove(workerId);
    }
}
