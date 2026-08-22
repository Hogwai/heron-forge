package dev.hogwai.platform.spi.data;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;

import java.util.List;

/**
 * Immutable, bounded materialization of a {@link Schema} as a list of
 * {@link SchemaRecord}s.
 *
 * <p>The row count is derived from the record list and the byte estimate is
 * non-negative. Construction fails with
 * {@link PlatformErrorCode#DATASET_LIMIT_EXCEEDED} when either the maximum row
 * count or the maximum byte estimate is exceeded. Framework-independent and
 * immutable.
 */
@SuppressWarnings("java:S6206")
public final class MaterializedDataSet {

    private final Schema schema;
    private final List<SchemaRecord> schemaRecords;
    private final DataSetMetadata metadata;
    private final long byteEstimate;

    /**
     * Creates a materialized data set.
     *
     * @param schema       the schema of the records
     * @param schemaRecords      the ordered, immutable list of records
     * @param metadata     the dataset metadata
     * @param byteEstimate the non-negative estimated size in bytes
     * @throws NullPointerException     if {@code schema}, {@code records} or
     *                                  {@code metadata} is {@code null}
     * @throws IllegalArgumentException if {@code byteEstimate} is negative, or
     *                                  if a record is bound to a schema distinct
     *                                  from the dataset schema
     * @throws PlatformException        with
     *                                  {@link PlatformErrorCode#DATASET_LIMIT_EXCEEDED}
     *                                  if the row count or byte estimate exceeds
     *                                  the configured limits
     */
    public MaterializedDataSet(Schema schema, List<SchemaRecord> schemaRecords, DataSetMetadata metadata, long byteEstimate) {
        DataSetValidator.validate(schema, schemaRecords, metadata, byteEstimate);
        this.schema = schema;
        this.schemaRecords = List.copyOf(schemaRecords);
        this.metadata = metadata;
        this.byteEstimate = byteEstimate;
    }

    /**
     * Returns the schema of the records.
     *
     * @return the schema of the records
     */
    public Schema schema() {
        return schema;
    }

    /**
     * Returns an immutable view of the records.
     *
     * @return an immutable view of the records
     */
    public List<SchemaRecord> records() {
        return schemaRecords;
    }

    /**
     * Returns the dataset metadata.
     *
     * @return the dataset metadata
     */
    public DataSetMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the number of records.
     *
     * @return the number of records
     */
    public long rowCount() {
        return schemaRecords.size();
    }

    /**
     * Returns the non-negative estimated size in bytes.
     *
     * @return the non-negative estimated size in bytes
     */
    public long byteEstimate() {
        return byteEstimate;
    }
}
