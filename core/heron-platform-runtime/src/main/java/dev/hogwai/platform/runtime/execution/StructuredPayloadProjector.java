package dev.hogwai.platform.runtime.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.host.StructuredPayload;

/** Converts SPI datasets to the generic value shape exposed by the Host API. */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class StructuredPayloadProjector {

    private StructuredPayloadProjector() {
        // no instances
    }

    public static StructuredPayload project(DataSet dataSet) {
        Objects.requireNonNull(dataSet, "dataSet must not be null");
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (dataSet instanceof StreamingDataSet streamed) {
            java.util.Optional<List<SchemaRecord>> batch = streamed.nextBatch();
            while (batch.isPresent()) {
                for (SchemaRecord schemaRecord : batch.get()) {
                    rows.add(toGenericRow(schemaRecord));
                }
                batch = streamed.nextBatch();
            }
        } else {
            MaterializedDataSet materialized = (MaterializedDataSet) dataSet;
            for (SchemaRecord schemaRecord : materialized.records()) {
                rows.add(toGenericRow(schemaRecord));
            }
        }
        root.put("rows", rows);
        root.put("rowCount", (long) rows.size());
        root.put("schemaId", dataSet.schema().identifier());
        root.put("schemaVersion", dataSet.schema().version());
        return new StructuredPayload(root);
    }

    /**
     * Converts one generationRecord to the generic row shape exposed by the Host API:
     * a map keyed by field name with normalized scalar values.
     *
     * @param schemaRecord the generationRecord to convert
     * @return the generic row
     */
    public static Map<String, Object> toGenericRow(SchemaRecord schemaRecord) {
        Map<String, Object> row = new LinkedHashMap<>();
        schemaRecord.values().forEach((field, value) -> row.put(field.value(), normalizeValue(value)));
        return row;
    }

    public static Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Long || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        throw new IllegalArgumentException("unsupported structured value type: " + value.getClass().getName());
    }
}
