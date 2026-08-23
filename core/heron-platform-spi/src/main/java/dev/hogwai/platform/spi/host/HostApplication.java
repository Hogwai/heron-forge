package dev.hogwai.platform.spi.host;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * Streams the entrypoint result in bounded batches when its target supports
     * streaming, or returns empty when only materialized invocation is
     * available. Implementations may fall back to empty for any reason — hosts
     * must then use {@link #invoke(InvocationRequest)}. When a failure occurs
     * before any batch could be pulled, implementations also return empty so
     * that the materialized path surfaces it as a regular error response.
     *
     * @param request invocation request metadata
     * @return the streaming payload, or empty to use materialized invocation
     */
    default Optional<StreamingPayload> invokeStreaming(InvocationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return Optional.empty();
    }

    /** Closes application-owned resources. */
    @Override
    void close();
}
