package dev.hogwai.platform.spi.data;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;

import java.util.List;
import java.util.Objects;

/**
 * Package-private validation helper for {@link MaterializedDataSet}.
 *
 * <p>Kept separate from {@link MaterializedDataSet} so that the public class
 * stays within the project's cyclomatic complexity budget while the validation
 * logic remains within the data package.
 */
final class DataSetValidator {

    private DataSetValidator() {
    }

    /**
     * Validates the arguments of a {@link MaterializedDataSet} construction.
     *
     * @param schema       the schema of the records
     * @param schemaRecords      the ordered list of records
     * @param metadata     the dataset metadata
     * @param byteEstimate the estimated size in bytes
     */
    static void validate(Schema schema, List<SchemaRecord> schemaRecords, DataSetMetadata metadata, long byteEstimate) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(schemaRecords, "records must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (byteEstimate < 0) {
            throw new IllegalArgumentException("byteEstimate must not be negative");
        }
        if (schemaRecords.stream().anyMatch(r -> !r.schema().equals(schema))) {
            throw new IllegalArgumentException("record schema does not match dataset schema");
        }
        DataSetLimits limits = metadata.limits();
        if (schemaRecords.size() > limits.maxRows() || byteEstimate > limits.maxBytes()) {
            throw new PlatformException(
                    PlatformErrorCode.DATASET_LIMIT_EXCEEDED,
                    List.of(Diagnostic.of(PlatformErrorCode.DATASET_LIMIT_EXCEEDED, Severity.ERROR,
                            "dataset limit exceeded")));
        }
    }
}
