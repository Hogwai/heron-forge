package dev.hogwai.platform.spi.data.access;

/**
 * Factory for opening data access instances.
 *
 * <p>A provider that opens a {@link DataAccess} owns that client. It must
 * register the client immediately with {@code BuildContext.resourceTracker()};
 * the runtime closes registered clients when the snapshot is closed. The
 * runtime does not automatically register data access handles.
 */
public interface DataAccessFactory {

    /**
     * Opens data access using the supplied configuration.
     *
     * @param configuration the data access configuration
     * @return an opened data access owned by the calling provider
     */
    DataAccess open(DataAccessConfiguration configuration);
}
