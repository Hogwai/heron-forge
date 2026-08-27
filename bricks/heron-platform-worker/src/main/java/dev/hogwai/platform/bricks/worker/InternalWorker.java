package dev.hogwai.platform.bricks.worker;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;

/**
 * Internal worker that delegates to an existing HostApplication.
 */
public record InternalWorker(String id, HostApplication host) implements AsyncWorker {

    /**
     * Creates an internal worker.
     *
     * @param id   the worker identifier
     * @param host the host application to delegate to
     */
    public InternalWorker {
        Objects.requireNonNull(id, "id is null");
        Objects.requireNonNull(host, "host is null");
    }

    @Override
    public List<EntrypointDescriptor> endpoints() {
        return host.entrypoints();
    }

    @Override
    public void invoke(WorkerInvocationRequest request, WorkerCompletionCallback callback) {
        Thread.startVirtualThread(() -> {
            try {
                InvocationRequest hostRequest = new InvocationRequest(
                        request.endpoint(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        Instant.now().plus(request.timeout()),
                        () -> false
                );
                ExecutionOutcome outcome = host.execute(hostRequest);
                callback.onComplete(outcome);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        });
    }
}
