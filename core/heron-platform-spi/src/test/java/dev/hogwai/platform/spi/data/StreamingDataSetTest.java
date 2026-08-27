package dev.hogwai.platform.spi.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;

/**
 * Verifies bounded streaming semantics: batches, limits, signals, closing.
 */
class StreamingDataSetTest {

    private static final Schema SCHEMA = new Schema("s", 1,
            List.of(new Field(new FieldId("value"), "value", new FieldType.StringType(), false, Optional.empty())),
            false);
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");

    @Test
    void yieldsBatchesUntilExhaustion() {
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, records(5).iterator(),
                new DataSetLimits(100, 100_000), 2, FAR_FUTURE, () -> false);

        assertThat(stream.nextBatch()).hasValueSatisfying(batch -> assertThat(batch).hasSize(2));
        assertThat(stream.nextBatch()).hasValueSatisfying(batch -> assertThat(batch).hasSize(2));
        assertThat(stream.nextBatch()).hasValueSatisfying(batch -> assertThat(batch).hasSize(1));
        assertThat(stream.nextBatch()).isEmpty();
        assertThat(stream.deliveredRowCount()).isEqualTo(5);
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        var rows = Collections.<SchemaRecord>emptyIterator();
        var limits = new DataSetLimits(10, 1_000);
        assertThatThrownBy(() -> StreamingDataSet.over(SCHEMA, rows, limits, 0, FAR_FUTURE, () -> false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be strictly positive");
    }

    @Test
    void failsFastWhenRowLimitExceeded() {
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, records(5).iterator(),
                new DataSetLimits(3, 1_000_000), 2, FAR_FUTURE, () -> false);

        assertThat(stream.nextBatch()).isPresent();
        assertThatThrownBy(stream::nextBatch)
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).code())
                .isEqualTo(PlatformErrorCode.DATASET_LIMIT_EXCEEDED);
        // Failed fast: the stream closed itself on the limit breach.
        assertThatThrownBy(stream::nextBatch).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastWhenByteLimitExceeded() {
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, records(5).iterator(),
                new DataSetLimits(100, 3L * StreamingDataSet.ROW_ESTIMATE_BYTES + 1),
                2, FAR_FUTURE, () -> false);

        assertThat(stream.nextBatch()).isPresent();
        assertThatThrownBy(stream::nextBatch)
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).code())
                .isEqualTo(PlatformErrorCode.DATASET_LIMIT_EXCEEDED);
    }

    @Test
    void honoursCancellationBetweenBatches() {
        AtomicInteger pulls = new AtomicInteger();
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, records(4).iterator(),
                new DataSetLimits(100, 100_000), 2, FAR_FUTURE,
                () -> pulls.incrementAndGet() > 1);

        assertThat(stream.nextBatch()).isPresent();
        assertThatThrownBy(stream::nextBatch)
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).code())
                .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED);
    }

    @Test
    void honoursDeadlineBetweenBatches() {
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, records(4).iterator(),
                new DataSetLimits(100, 100_000), 2, Instant.parse("2000-01-01T00:00:00Z"), () -> false);

        assertThatThrownBy(stream::nextBatch)
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).code())
                .isEqualTo(PlatformErrorCode.DEADLINE_EXCEEDED);
    }

    @Test
    void closeIsIdempotentAndReleasesAutoCloseableSource() {
        CloseableIterator rows = new CloseableIterator(records(2).iterator());
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, rows,
                new DataSetLimits(100, 100_000), 2, FAR_FUTURE, () -> false);

        assertThat(stream.nextBatch()).isPresent();
        stream.close();
        stream.close();
        assertThat(rows.closed).isTrue();
        assertThatThrownBy(stream::nextBatch).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toMaterializedClosesTheUnderlyingCursor() {
        CloseableIterator rows = new CloseableIterator(records(3).iterator());
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, rows,
                new DataSetLimits(100, 100_000), 2, FAR_FUTURE, () -> false);

        MaterializedDataSet materialized = stream.toMaterialized();

        assertThat(materialized.records()).hasSize(3);
        assertThat(rows.closed).isTrue();
        assertThatThrownBy(stream::nextBatch).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toMaterializedClosesEvenWhenDrainFails() {
        CloseableIterator rows = new CloseableIterator(records(5).iterator());
        StreamingDataSet stream = StreamingDataSet.over(SCHEMA, rows,
                new DataSetLimits(2, 100_000), 2, FAR_FUTURE, () -> false);

        assertThatThrownBy(stream::toMaterialized)
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).code())
                .isEqualTo(PlatformErrorCode.DATASET_LIMIT_EXCEEDED);
        assertThat(rows.closed).isTrue();
    }

    private static List<SchemaRecord> records(int count) {
        List<SchemaRecord> records = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            records.add(SchemaRecord.of(SCHEMA, java.util.Map.of(new FieldId("value"), "row-" + index)));
        }
        return records;
    }

    private static final class CloseableIterator implements java.util.Iterator<SchemaRecord>, AutoCloseable {

        private final java.util.Iterator<SchemaRecord> delegate;
        private boolean closed;

        private CloseableIterator(java.util.Iterator<SchemaRecord> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public SchemaRecord next() {
            return delegate.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
