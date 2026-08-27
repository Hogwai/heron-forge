package dev.hogwai.platform.spi.host;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic, dependency-free streaming view of an invocation result: rows are
 * plain maps keyed by field name with normalized scalar values (strings,
 * booleans, longs, decimals and ISO-8601 instants as strings).
 *
 * <p>Hosts pull bounded batches until {@link #nextBatch()} returns empty; the
 * cumulative delivered count grows accordingly. Closing the payload releases
 * the underlying resources and is idempotent.
 */
public interface StreamingPayload extends AutoCloseable {

    /**
     * Returns the next bounded batch of rows, or empty once exhausted.
     *
     * @return the next batch of generic rows, or empty at exhaustion
     */
    Optional<List<Map<String, Object>>> nextBatch();

    /**
     * Returns the schema identifier the rows conform to.
     *
     * @return the schema identifier
     */
    String schemaId();

    /**
     * Returns the schema version the rows conform to.
     *
     * @return the schema version
     */
    int schemaVersion();

    /**
     * Returns the number of rows delivered so far.
     *
     * @return delivered row count
     */
    long deliveredRowCount();

    /**
     * Releases the underlying resources; idempotent.
     */
    @Override
    void close();
}
