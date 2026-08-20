package dev.hogwai.platform.spi.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SchemaSchemaRecordTest {

    private static final FieldId ID = new FieldId("id");
    private static final FieldId COUNT = new FieldId("count");

    private static Map<FieldId, Object> mapWithNull(FieldId id) {
        Map<FieldId, Object> map = new HashMap<>();
        map.put(id, null);
        return map;
    }

    @Test
    void recordDistinguishesAbsentFromPresentNull() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), true, Optional.empty())), false);
        SchemaRecord schemaRecord = SchemaRecord.of(schema, mapWithNull(ID));
        assertThat(schemaRecord.contains(ID)).isTrue();
        assertThat(schemaRecord.get(ID)).isNull();
        SchemaRecord absent = SchemaRecord.of(schema, Map.of());
        assertThat(absent.contains(ID)).isFalse();
        assertThat(absent.get(ID)).isNull();
    }

    @Test
    void recordRejectsMissingRequiredField() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
        Map<FieldId, Object> noValues = Map.of();
        assertThatThrownBy(() -> SchemaRecord.of(schema, noValues)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordAcceptsOptionalFieldAbsent() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), true, Optional.empty())), false);
        SchemaRecord schemaRecord = SchemaRecord.of(schema, Map.of());
        assertThat(schemaRecord.contains(ID)).isFalse();
    }

    @Test
    void recordRejectsUnknownFieldWhenClosed() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
        Map<FieldId, Object> withUnknown = Map.of(ID, "x", new FieldId("extra"), "y");
        assertThatThrownBy(() -> SchemaRecord.of(schema, withUnknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordAcceptsUnknownFieldWhenOpen() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), true);
        SchemaRecord schemaRecord = SchemaRecord.of(schema, Map.of(ID, "x", new FieldId("extra"), "y"));
        assertThat(schemaRecord.contains(new FieldId("extra"))).isTrue();
        assertThat(schemaRecord.get(new FieldId("extra"))).isEqualTo("y");
    }

    @Test
    void recordRejectsNullForNonNullableField() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
        Map<FieldId, Object> withNull = mapWithNull(ID);
        assertThatThrownBy(() -> SchemaRecord.of(schema, withNull)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordRejectsValueNotAssignableToType() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
        Map<FieldId, Object> wrongType = Map.of(ID, 1L);
        assertThatThrownBy(() -> SchemaRecord.of(schema, wrongType))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordAppliesDefaultWithoutErasingAbsentDistinction() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(COUNT, "Count", new FieldType.Int64Type(), false, Optional.of(5L))), false);
        SchemaRecord schemaRecord = SchemaRecord.of(schema, Map.of());
        assertThat(schemaRecord.contains(COUNT)).isFalse();
        assertThat(schemaRecord.value(COUNT)).isEqualTo(5L);
        assertThat(schemaRecord.get(COUNT)).isNull();
    }

    @Test
    void recordRejectsMutableCollectionForOpenField() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), true);
        Map<FieldId, Object> withCollection = Map.of(ID, "x", new FieldId("extra"), List.of("a"));
        assertThatThrownBy(() -> SchemaRecord.of(schema, withCollection))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordAcceptsScalarForOpenField() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), true);
        SchemaRecord schemaRecord = SchemaRecord.of(schema, Map.of(ID, "x", new FieldId("extra"), 42L));
        assertThat(schemaRecord.get(new FieldId("extra"))).isEqualTo(42L);
    }

    @Test
    void recordAcceptsLongDecimalAndInstantForOpenFields() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), true);
        Instant instant = Instant.now();
        SchemaRecord schemaRecord = SchemaRecord.of(schema, Map.of(
                ID, "x",
                new FieldId("l"), 42L,
                new FieldId("d"), new BigDecimal("1.5"),
                new FieldId("i"), instant));
        assertThat(schemaRecord.get(new FieldId("l"))).isEqualTo(42L);
        assertThat(schemaRecord.get(new FieldId("d"))).isEqualTo(new BigDecimal("1.5"));
        assertThat(schemaRecord.get(new FieldId("i"))).isEqualTo(instant);
    }

    @Test
    void recordValuesAreImmutable() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
        SchemaRecord schemaRecord = SchemaRecord.of(schema, Map.of(ID, "x"));
        Map<FieldId, Object> valuesView = schemaRecord.values();
        FieldId extraId = new FieldId("z");
        assertThatThrownBy(() -> valuesView.put(extraId, "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
