package dev.hogwai.platform.spi.invocation;

import java.util.List;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.HostApplication;

/**
 * Worker for asynchronous tasks
 */
public interface AsyncWorker {

    /**
     * Returns the unique worker identifier.
     *
     * @return the worker identifier
     */
    String id();

    /**
     * Returns the host application exposing this worker.
     *
     * @return the host application
     */
    HostApplication host();

    /**
     * Returns the available endpoint descriptors.
     *
     * @return the endpoint descriptors
     */
    List<EntrypointDescriptor> endpoints();

    /**
     * Invokes the worker asynchronously.
     *
     * <p>The implementation must be non-blocking. The callback is called
     * when the invocation completes or fails.
     *
     * @param request  the invocation request
     * @param callback the completion callback
     */
    void invoke(WorkerInvocationRequest request, WorkerCompletionCallback callback);
}
