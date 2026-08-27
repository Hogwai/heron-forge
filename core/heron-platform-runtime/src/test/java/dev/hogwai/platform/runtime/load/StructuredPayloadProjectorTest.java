package dev.hogwai.platform.runtime.load;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.hogwai.platform.runtime.execution.StructuredPayloadProjector;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.host.StructuredPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredPayloadProjectorTest {

    @Test
    void projectsRowsAndEnvelopeInStableOrderAndNormalizesInstant() {
        FieldId name = new FieldId("name");
        FieldId enabled = new FieldId("enabled");
        FieldId count = new FieldId("count");
        FieldId amount = new FieldId("amount");
        FieldId observedAt = new FieldId("observed_at");
        Schema schema = new Schema("orders", 3, List.of(
                new Field(name, "Name", new FieldType.StringType(), false, Optional.empty()),
                new Field(enabled, "Enabled", new FieldType.BooleanType(), false, Optional.empty()),
                new Field(count, "Count", new FieldType.Int64Type(), false, Optional.empty()),
                new Field(amount, "Amount", new FieldType.DecimalType(), false, Optional.empty()),
                new Field(observedAt, "Observed at", new FieldType.InstantType(), false, Optional.empty())), false);
        Map<FieldId, Object> values = new LinkedHashMap<>();
        values.put(name, "first");
        values.put(enabled, true);
        values.put(count, 2L);
        values.put(amount, new BigDecimal("12.50"));
        values.put(observedAt, Instant.parse("2026-01-01T00:00:00Z"));
        MaterializedDataSet dataSet = new MaterializedDataSet(schema,
                List.of(SchemaRecord.of(schema, values)),
                new DataSetMetadata("orders", new DataSetLimits(10, 10_000)), 100);

        StructuredPayload payload = StructuredPayloadProjector.project(dataSet);
        assertThat(payload.value().keySet()).containsExactly("rows", "rowCount", "schemaId", "schemaVersion");
        assertThat(payload.value()).containsEntry("rowCount", 1L);
        assertThat(payload.value()).containsEntry("schemaId", "orders");
        assertThat(payload.value()).containsEntry("schemaVersion", 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) ((List<?>) payload.value().get("rows")).getFirst();
        assertThat(row.keySet()).containsExactly("name", "enabled", "count", "amount", "observed_at");
        assertThat(row).containsEntry("observed_at", "2026-01-01T00:00:00Z")
                .containsEntry("count", 2L)
                .containsEntry("amount", new BigDecimal("12.50"));
    }

    @Test
    void rejectsUnsupportedValuesBeforeHostPayloadConstruction() {
        var object = new Object();
        assertThatThrownBy(() -> StructuredPayloadProjector.normalizeValue(object))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported structured value type");
        assertThatThrownBy(() -> StructuredPayloadProjector.project(null))
                .isInstanceOf(NullPointerException.class);
    }
}
