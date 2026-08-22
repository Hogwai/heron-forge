package dev.hogwai.platform.spi.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaSchemaTest {

    private static final FieldId ID = new FieldId("id");
    private static final FieldId NAME = new FieldId("name");
    private static final FieldId COUNT = new FieldId("count");

    @Test
    void schemaAcceptsValidFields() {
        Schema schema = new Schema("orders", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty()),
                new Field(COUNT, "Count", new FieldType.Int64Type(), true, Optional.empty())), false);
        assertThat(schema.identifier()).isEqualTo("orders");
        assertThat(schema.version()).isEqualTo(1);
        assertThat(schema.fields()).hasSize(2);
        assertThat(schema.openFields()).isFalse();
        assertThat(schema.field(ID)).isPresent();
        assertThat(schema.field(new FieldId("missing"))).isEmpty();
    }

    @Test
    void schemaRejectsBlankIdentifier() {
        List<Field> noFields = List.of();
        assertThatThrownBy(() -> new Schema("", 1, noFields, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaRejectsNonPositiveVersion() {
        List<Field> noFields = List.of();
        assertThatThrownBy(() -> new Schema("s", 0, noFields, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Schema("s", -1, noFields, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaRejectsDuplicateFieldIds() {
        List<Field> duplicateIds = List.of(
                new Field(ID, "A", new FieldType.StringType(), false, Optional.empty()),
                new Field(ID, "B", new FieldType.StringType(), false, Optional.empty()));
        assertThatThrownBy(() -> new Schema("s", 1, duplicateIds, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaRejectsDuplicateFieldNames() {
        List<Field> duplicateNames = List.of(
                new Field(ID, "Same", new FieldType.StringType(), false, Optional.empty()),
                new Field(NAME, "Same", new FieldType.StringType(), false, Optional.empty()));
        assertThatThrownBy(() -> new Schema("s", 1, duplicateNames, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaFieldsAreImmutable() {
        Schema schema = new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
        List<Field> fieldsView = schema.fields();
        assertThatThrownBy(fieldsView::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
