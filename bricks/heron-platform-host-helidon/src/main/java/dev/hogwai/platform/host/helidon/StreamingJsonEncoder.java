package dev.hogwai.platform.host.helidon;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.hogwai.platform.spi.host.StreamingPayload;

/**
 * Serializes a {@link StreamingPayload} as the same JSON shape produced for
 * materialized payloads: generic rows are emitted incrementally through
 * Jackson's streaming {@link JsonGenerator} while batches are pulled, and the
 * trailing rowCount, schemaId and schemaVersion close the document once the
 * stream is exhausted.
 */
final class StreamingJsonEncoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StreamingJsonEncoder() {
        // no instances
    }

    /**
     * Encodes the payload, starting with an already-pulled first batch so that
     * callers could still report an early failure with a proper HTTP status.
     *
     * @param payload    the streaming payload
     * @param firstBatch the first batch pulled by the caller
     * @param out        the target stream
     * @throws IOException when writing fails
     */
    static void encode(StreamingPayload payload,
                       List<Map<String, Object>> firstBatch,
                       OutputStream out)
            throws IOException {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(firstBatch, "firstBatch must not be null");
        try (JsonGenerator generator = MAPPER.getFactory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeFieldName("rows");
            generator.writeStartArray();
            long rowCount = emit(payload, firstBatch, generator);
            generator.writeEndArray();
            generator.writeNumberField("rowCount", rowCount);
            generator.writeStringField("schemaId", payload.schemaId());
            generator.writeNumberField("schemaVersion", payload.schemaVersion());
            generator.writeEndObject();
        }
    }

    private static long emit(StreamingPayload payload,
                             List<Map<String, Object>> firstBatch,
                             JsonGenerator generator) throws IOException {
        long rowCount = 0;
        List<Map<String, Object>> batch = firstBatch;
        while (batch != null) {
            for (Map<String, Object> row : batch) {
                generator.writeObject(row);
                rowCount++;
            }
            batch = payload.nextBatch().orElse(null);
        }
        return rowCount;
    }
}

