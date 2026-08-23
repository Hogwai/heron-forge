package dev.hogwai.platform.host.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.hogwai.platform.spi.host.StreamingPayload;

/** Verifies the wire format of streamed responses. */
class StreamingJsonEncoderTest {

    @Test
    void encodesBatchesAsOneDocumentWithTrailingRowCount() throws IOException {
        StreamingPayload payload = new FakePayload(
                List.of(
                        List.of(Map.of("orderId", "row-0"), Map.of("orderId", "row-1")),
                        List.of(Map.of("orderId", "row-2"))),
                "orders");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamingJsonEncoder.encode(payload,
                payload.nextBatch().orElseThrow(), out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"rows":[{"orderId":"row-0"},{"orderId":"row-1"},{"orderId":"row-2"}],\
                "rowCount":3,"schemaId":"orders","schemaVersion":1}""");
        assertThat(payload.deliveredRowCount()).isEqualTo(3);
    }

    private static final class FakePayload implements StreamingPayload {

        private final List<List<Map<String, Object>>> batches;
        private final String schemaId;
        private Iterator<List<Map<String, Object>>> cursor;
        private long delivered;

        private FakePayload(List<List<Map<String, Object>>> batches, String schemaId) {
            this.batches = batches;
            this.schemaId = schemaId;
        }

        @Override
        public Optional<List<Map<String, Object>>> nextBatch() {
            if (cursor == null) {
                cursor = batches.iterator();
            }
            if (!cursor.hasNext()) {
                return Optional.empty();
            }
            List<Map<String, Object>> batch = cursor.next();
            delivered += batch.size();
            return Optional.of(batch);
        }

        @Override
        public String schemaId() {
            return schemaId;
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public long deliveredRowCount() {
            return delivered;
        }

        @Override
        public void close() {
            // No-op
        }
    }
}
