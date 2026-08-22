package dev.hogwai.platform.spi.host;

/** Synchronous lifecycle contract for a host transport adapter. */
public interface HostAdapter extends AutoCloseable {

    /**
     * Starts serving the application with the supplied configuration.
     *
     * @param application application to serve
     * @param configuration host configuration
     * @throws HostException if startup fails
     */
    void start(HostApplication application, HostConfiguration configuration) throws HostException;

    /**
     * Returns whether the adapter has completed startup.
     *
     * @return whether startup completed successfully
     */
    boolean ready();

    /**
     * Stops serving and releases adapter resources.
     *
     * @throws HostException if shutdown fails
     */
    void stop() throws HostException;

    /** Closes the adapter. */
    @Override
    void close();
}
