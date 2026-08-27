package dev.hogwai.platform.spi.invocation;

import java.util.List;
import java.util.Optional;

/**
 * Registry for {@link AsyncWorker} instances.
 *
 * <p>Providers can discover workers at build-time via {@link dev.hogwai.platform.spi.provider.BuildContext}
 * and store references for use during execution.
 */
public interface WorkerRegistry {

    /**
     * Registers a worker.
     *
     * @param worker the worker to register
     */
    void register(AsyncWorker worker);

    /**
     * Finds a worker by identifier.
     *
     * @param workerId the worker identifier
     * @return the worker if present
     */
    Optional<AsyncWorker> find(String workerId);

    /**
     * Returns all registered workers.
     *
     * @return the list of workers
     */
    List<AsyncWorker> all();
}
