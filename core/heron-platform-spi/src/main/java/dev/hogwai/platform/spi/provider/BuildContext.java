package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.invocation.WorkerRegistry;

import java.time.Clock;
import java.util.Objects;

/**
 * Immutable context passed to {@link ProviderFactory#create}.
 *
 * <p>It exposes only a clock, resource tracker and data access factory.
 * Providers that open a data access client own that client, must register it
 * immediately with {@link #resourceTracker()}, and rely on the runtime to
 * close it with the snapshot.
 */
public record BuildContext(Clock clock, ResourceTracker resourceTracker, DataAccessFactory dataAccessFactory,
                           WorkerRegistry workerRegistry) {

    /**
     * Creates a build context.
     *
     * @param clock             clock
     * @param resourceTracker   resource tracker
     * @param dataAccessFactory data access factory
     * @param workerRegistry    worker registry
     * @throws NullPointerException if any argument is null
     */
    public BuildContext(Clock clock, ResourceTracker resourceTracker, DataAccessFactory dataAccessFactory,
                        WorkerRegistry workerRegistry) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.resourceTracker = Objects.requireNonNull(resourceTracker, "resourceTracker must not be null");
        this.dataAccessFactory = Objects.requireNonNull(dataAccessFactory, "dataAccessFactory must not be null");
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "workerRegistry must not be null");
    }

    /**
     * Creates a build context without a worker registry (empty registry).
     *
     * @param clock             clock
     * @param resourceTracker   resource tracker
     * @param dataAccessFactory data access factory
     * @throws NullPointerException if any argument is null
     */
    public BuildContext(Clock clock, ResourceTracker resourceTracker, DataAccessFactory dataAccessFactory) {
        this(clock, resourceTracker, dataAccessFactory, new NoopWorkerRegistry());
    }

    private static final class NoopWorkerRegistry implements WorkerRegistry {
        @Override
        public void register(dev.hogwai.platform.spi.invocation.AsyncWorker worker) {
            // no-op
        }

        @Override
        public java.util.Optional<dev.hogwai.platform.spi.invocation.AsyncWorker> find(String workerId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<dev.hogwai.platform.spi.invocation.AsyncWorker> all() {
            return java.util.List.of();
        }
    }
}
