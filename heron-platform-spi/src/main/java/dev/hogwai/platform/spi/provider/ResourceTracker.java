package dev.hogwai.platform.spi.provider;

/**
 * Tracks resources registered by a provider so they can be released with the
 * capability instance.
 *
 * <p>Implementations are responsible for the lifecycle and thread-safety of the
 * registered resources. Framework-independent.
 */
public interface ResourceTracker {

    /**
     * Registers a resource to be released with the capability instance.
     *
     * @param resource the resource to register
     */
    void register(AutoCloseable resource);
}
