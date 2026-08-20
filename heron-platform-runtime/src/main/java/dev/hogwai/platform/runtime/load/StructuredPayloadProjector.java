package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.host.api.StructuredPayload;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts SPI datasets to the generic value shape exposed by the Host API. */
final class StructuredPayloadProjector {

    private StructuredPayloadProjector() {
        // no instances
    }

    static StructuredPayload project(MaterializedDataSet dataSet) {
        Objects.requireNonNull(dataSet, "dataSet must not be null");
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SchemaRecord record : dataSet.records()) {
            Map<String, Object> row = new LinkedHashMap<>();
            record.values().forEach((field, value) -> row.put(field.value(), normalizeValue(value)));
            rows.add(row);
        }
        root.put("rows", rows);
        root.put("rowCount", dataSet.rowCount());
        root.put("schemaId", dataSet.schema().identifier());
        root.put("schemaVersion", dataSet.schema().version());
        return new StructuredPayload(root);
    }

    static Object normalizeValue(Object value) {
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
