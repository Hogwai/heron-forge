package dev.hogwai.platform.spi.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;

/**
 * Lazy, bounded streaming view of a {@link Schema} as batches of
 * {@link SchemaRecord}s.
 *
 * <p>Unlike {@link MaterializedDataSet}, no full result is held in memory:
 * consumers pull bounded batches through {@link #nextBatch()} until it returns
 * empty. Every pull re-checks the cancellation signal and the execution
 * deadline, and the cumulative row count and byte estimate are enforced against
 * {@link DataSetLimits} exactly like a materialized data set, exceeding either
 * fails fast with {@link PlatformErrorCode#DATASET_LIMIT_EXCEEDED}.
 *
 * <p>The dataset owns its underlying cursor: {@link #close()} releases it and is
 * idempotent. When the supplied iterator implements {@link AutoCloseable} (as
 * JDBC-backed cursors do), closing the dataset closes it too.
 */
public non-sealed interface StreamingDataSet extends AutoCloseable, DataSet {

    /**
     * Collects the remaining batches into a materialized dataset, for consumers
     * that require in-memory rows (upstream composition). The stream is closed.
     *
     * @return the collected materialized dataset
     */
    default MaterializedDataSet toMaterialized() {
        List<SchemaRecord> rows = new ArrayList<>();
        try (StreamingDataSet stream = this) {
            Optional<List<SchemaRecord>> batch = stream.nextBatch();
            while (batch.isPresent()) {
                rows.addAll(batch.get());
                batch = stream.nextBatch();
            }
        }
        long bytes = rows.size() * ROW_ESTIMATE_BYTES;
        return new MaterializedDataSet(schema(), rows,
                new DataSetMetadata("streamed", new DataSetLimits(Math.max(1, rows.size()), Math.max(1, bytes))),
                bytes);
    }

    /** Byte estimate charged per streamed record, mirroring MaterializedDataSet. */
    long ROW_ESTIMATE_BYTES = 256L;

    /**
     * Returns the schema every streamed record conforms to.
     *
     * @return the schema
     */
    @Override
    Schema schema();

    /**
     * Returns the next bounded batch of records, or empty once exhausted.
     *
     * @return the next batch, or empty at exhaustion
     * @throws PlatformException   when canceled, past the deadline, or over limits
     * @throws IllegalStateException when the dataset is closed
     */
    Optional<List<SchemaRecord>> nextBatch();

    /**
     * Returns the number of records delivered so far.
     *
     * @return delivered record count
     */
    long deliveredRowCount();

    /** Releases the underlying cursor; idempotent. */
    @Override
    void close();

    /**
     * Wraps a row iterator into a bounded, deadline- and cancellation-aware
     * streaming dataset.
     *
     * @param schema      the schema records conform to
     * @param rows        the lazy row source; closed on {@link #close()} when it
     *                    implements {@link AutoCloseable}
     * @param limits      cumulative row and byte bounds
     * @param batchSize   maximum records per batch, strictly positive
     * @param deadline    execution deadline re-checked before every batch
     * @param cancelled   cancellation signal re-checked before every batch
     * @return the streaming dataset
     */
    static StreamingDataSet over(Schema schema, Iterator<SchemaRecord> rows,
            DataSetLimits limits, int batchSize, Instant deadline, BooleanSupplier cancelled) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancelled, "cancelled must not be null");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be strictly positive");
        }
        return new Bounded(schema, rows, limits, batchSize, deadline, cancelled);
    }

    /** Default implementation enforcing bounds between pulls. */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    final class Bounded implements StreamingDataSet {

        private final Schema schema;
        private final Iterator<SchemaRecord> rows;
        private final DataSetLimits limits;
        private final int batchSize;
        private final Instant deadline;
        private final BooleanSupplier cancelled;
        private long deliveredRowCount;
        private long byteEstimate;
        private boolean closed;

        private Bounded(Schema schema,
                        Iterator<SchemaRecord> rows,
                        DataSetLimits limits,
                        int batchSize,
                        Instant deadline,
                        BooleanSupplier cancelled) {
            this.schema = schema;
            this.rows = rows;
            this.limits = limits;
            this.batchSize = batchSize;
            this.deadline = deadline;
            this.cancelled = cancelled;
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public Optional<List<SchemaRecord>> nextBatch() {
            if (closed) {
                throw new IllegalStateException("streaming data set is closed");
            }
            checkSignals();
            if (!rows.hasNext()) {
                return Optional.empty();
            }
            List<SchemaRecord> batch = new ArrayList<>(batchSize);
            while (batch.size() < batchSize && rows.hasNext()) {
                batch.add(rows.next());
                deliveredRowCount++;
                byteEstimate += ROW_ESTIMATE_BYTES;
                enforceLimits();
            }
            return Optional.of(List.copyOf(batch));
        }

        @Override
        public long deliveredRowCount() {
            return deliveredRowCount;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (rows instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception _) {
                    throw new PlatformException(
                            PlatformErrorCode.CAPABILITY_EXECUTION_ERROR,
                            List.of(Diagnostic.of(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR,
                                    "streaming data set failed to release its cursor"))
                    );
                }
            }
        }

        private void checkSignals() {
            if (cancelled.getAsBoolean()) {
                close();
                throw failure("streamed data operation was cancelled", PlatformErrorCode.CANCELLATION_REQUESTED);
            }
            if (!Instant.now().isBefore(deadline)) {
                close();
                throw failure("streamed data operation exceeded its deadline",
                        PlatformErrorCode.DEADLINE_EXCEEDED);
            }
        }

        private void enforceLimits() {
            if (deliveredRowCount > limits.maxRows() || byteEstimate > limits.maxBytes()) {
                close();
                throw failure("dataset limit exceeded", PlatformErrorCode.DATASET_LIMIT_EXCEEDED);
            }
        }

        private static PlatformException failure(String message, PlatformErrorCode code) {
            return new PlatformException(code, List.of(Diagnostic.of(code, Severity.ERROR, message)));
        }
    }
}
