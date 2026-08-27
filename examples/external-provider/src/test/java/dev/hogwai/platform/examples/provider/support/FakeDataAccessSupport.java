package dev.hogwai.platform.examples.provider.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.access.DataRow;

/**
 * Shared mapping helpers for fake data access implementations in tests.
 */
public final class FakeDataAccessSupport {

    /**
     * Default dataset limits matching the brick defaults.
     */
    public static final DataSetLimits DEFAULT_LIMITS = new DataSetLimits(1_000, 1_000_000);

    private FakeDataAccessSupport() {
    }

    /**
     * Maps a row to a schema record using the field-to-column mapping.
     */
    public static SchemaRecord toRecord(DataRow row, Schema schema, Map<String, String> columnByField) {
        Map<FieldId, Object> values = new HashMap<>();
        for (Field field : schema.fields()) {
            String column = columnByField.get(field.id().value());
            if (column == null) {
                throw new IllegalArgumentException("no column mapped for field " + field.id().value());
            }
            values.put(field.id(), readValue(row, column, field.type()));
        }
        return SchemaRecord.of(schema, values);
    }

    /**
     * Builds a materialized data set with the supplied limits.
     */
    public static MaterializedDataSet dataSet(Schema schema, String name, List<SchemaRecord> records,
                                              DataSetLimits limits) {
        long estimate = Math.multiplyExact(records.size(), 256L);
        return new MaterializedDataSet(schema, records,
                new DataSetMetadata(name, limits), estimate);
    }

    private static Object readValue(DataRow row, String column, FieldType type) {
        if (type instanceof FieldType.StringType) {
            return row.string(column);
        }
        if (type instanceof FieldType.Int64Type) {
            return row.longValue(column);
        }
        if (type instanceof FieldType.InstantType) {
            return row.instant(column);
        }
        throw new IllegalArgumentException("unsupported field type for column " + column + ": " + type);
    }
}