package dev.hogwai.platform.runtime.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.host.StructuredPayload;

/** Converts SPI datasets to the generic value shape exposed by the Host API. */
public final class StructuredPayloadProjector {

    private StructuredPayloadProjector() {
        // no instances
    }

    public static StructuredPayload project(MaterializedDataSet dataSet) {
        Objects.requireNonNull(dataSet, "dataSet must not be null");
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SchemaRecord schemaRecord : dataSet.records()) {
            Map<String, Object> row = new LinkedHashMap<>();
            schemaRecord.values().forEach((field, value) -> row.put(field.value(), normalizeValue(value)));
            rows.add(row);
        }
        root.put("rows", rows);
        root.put("rowCount", dataSet.rowCount());
        root.put("schemaId", dataSet.schema().identifier());
        root.put("schemaVersion", dataSet.schema().version());
        return new StructuredPayload(root);
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
