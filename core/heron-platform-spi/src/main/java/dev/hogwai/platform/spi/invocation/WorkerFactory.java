package dev.hogwai.platform.spi.invocation;

import java.util.Map;

/**
 * Factory for {@link AsyncWorker} instances.
 */
public interface WorkerFactory {

    /**
     * Returns the transport supported by this factory.
     *
     * @return the transport identifier
     */
    String transport();

    /**
     * Creates a worker.
     *
     * @param id     the worker identifier
     * @param config the safe configuration values
     * @return the worker
     */
    AsyncWorker create(String id, Map<String, Object> config);
}
