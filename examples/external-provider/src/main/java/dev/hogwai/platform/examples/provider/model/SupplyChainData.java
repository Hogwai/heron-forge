package dev.hogwai.platform.examples.provider.model;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dataset construction and typed record access for the example.
 */
public final class SupplyChainData {

    private SupplyChainData() {
    }

    public static SchemaRecord schemaRecord(Schema schema, Map<String, Object> values) {
        Map<FieldId, Object> fields = values.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> new FieldId(entry.getKey()), Map.Entry::getValue));
        return SchemaRecord.of(schema, fields);
    }

    public static MaterializedDataSet dataSet(Schema schema, String name, List<SchemaRecord> records) {
        long estimate = Math.multiplyExact(records.size(), 256L);
        return new MaterializedDataSet(schema, records,
                new DataSetMetadata(name, new DataSetLimits(1_000, 1_000_000)), estimate);
    }

    public static String string(SchemaRecord schemaRecord, String field) {
        return (String) schemaRecord.value(new FieldId(field));
    }

    public static long longValue(SchemaRecord schemaRecord, String field) {
        return (Long) schemaRecord.value(new FieldId(field));
    }

    public static Instant instant(SchemaRecord schemaRecord, String field) {
        return (Instant) schemaRecord.value(new FieldId(field));
    }
}
