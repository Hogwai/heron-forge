package dev.hogwai.platform.spi.host;

import java.util.List;

/** Application contract owned by a host. */
public interface HostApplication extends AutoCloseable {

    /**
     * Returns the configured entrypoints.
     *
     * @return configured entrypoints
     */
    List<EntrypointDescriptor> entrypoints();

    /**
     * Executes an entrypoint exactly once and returns a shape-adaptive
     * outcome: materialized rows, a generic streaming payload, or a failure.
     * Hosts branch on the outcome instead of probing separate methods, so the
     * underlying graph runs once per request. When the receiver takes the
     * streaming payload it owns it and must close it.
     *
     * @param request invocation request metadata
     * @return the execution outcome
     */
    ExecutionOutcome execute(InvocationRequest request);

    /** Closes application-owned resources. */
    @Override
    void close();
}
