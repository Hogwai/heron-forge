package dev.hogwai.platform.host.api;

import java.util.List;

/** Framework-independent application contract owned by a host. */
public interface HostApplication extends AutoCloseable {

    /**
     * Returns the configured entrypoints.
     *
     * @return configured entrypoints
     */
    List<EntrypointDescriptor> entrypoints();

    /**
     * Invokes an entrypoint synchronously.
     *
     * @param request invocation request metadata
     * @return invocation result
     */
    InvocationResult invoke(InvocationRequest request);

    /** Closes application-owned resources. */
    @Override
    void close();
}
