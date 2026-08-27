package dev.hogwai.platform.runtime.invocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerRegistry;

/**
 * In-memory implementation of {@link WorkerRegistry}.
 */
public final class InMemoryWorkerRegistry implements WorkerRegistry {

    private final Map<String, AsyncWorker> workers = new ConcurrentHashMap<>();

    @Override
    public void register(AsyncWorker worker) {
        workers.put(worker.id(), worker);
    }

    @Override
    public Optional<AsyncWorker> find(String workerId) {
        return Optional.ofNullable(workers.get(workerId));
    }

    @Override
    public List<AsyncWorker> all() {
        return List.copyOf(workers.values());
    }
}
