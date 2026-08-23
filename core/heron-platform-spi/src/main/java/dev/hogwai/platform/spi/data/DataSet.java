package dev.hogwai.platform.spi.data;

/**
 * Common view of capability results: a dataset is either materialized
 * ({@link MaterializedDataSet}, fully in memory) or streaming
 * ({@link StreamingDataSet}, lazy bounded batches). Capability instances pick
 * their result type via the covariant return of {@code execute}; consumers
 * that require in-memory rows collect streaming datasets through
 * {@link StreamingDataSet#toMaterialized()}.
 */
public sealed interface DataSet permits MaterializedDataSet, StreamingDataSet {

    /**
     * Returns the schema every record conforms to.
     *
     * @return the schema
     */
    Schema schema();
}
