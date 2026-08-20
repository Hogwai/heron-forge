package dev.hogwai.platform.runtime.compile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SchemaCompatibilityTypesTest {

    private static final FieldId ID = new FieldId("id");
    private static final FieldId COUNT = new FieldId("count");

    private static Field field(FieldId id, FieldType type, boolean nullable) {
        return new Field(id, id.value(), type, nullable, Optional.empty());
    }

    private static Schema schema(boolean open, Field... fields) {
        return new Schema("s", 1, List.of(fields), open);
    }

    @Test
    void openOutputCannotFeedClosedInput() {
        Schema output = schema(true, field(ID, new FieldType.StringType(), false));
        Schema input = schema(false, field(ID, new FieldType.StringType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isFalse();
    }

    @Test
    void openOutputCanFeedOpenInput() {
        Schema output = schema(true, field(ID, new FieldType.StringType(), false));
        Schema input = schema(true, field(ID, new FieldType.StringType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void booleanTypesAreCompatible() {
        Schema output = schema(false, field(ID, new FieldType.BooleanType(), false));
        Schema input = schema(false, field(ID, new FieldType.BooleanType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void decimalTypesAreCompatible() {
        Schema output = schema(false, field(ID, new FieldType.DecimalType(), false));
        Schema input = schema(false, field(ID, new FieldType.DecimalType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void int64TypesAreCompatible() {
        Schema output = schema(false, field(COUNT, new FieldType.Int64Type(), false));
        Schema input = schema(false, field(COUNT, new FieldType.Int64Type(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void instantTypesAreCompatible() {
        Schema output = schema(false, field(ID, new FieldType.InstantType(), false));
        Schema input = schema(false, field(ID, new FieldType.InstantType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void scalarTypeMismatchesAreIncompatible() {
        assertThat(SchemaCompatibility.check(
                schema(false, field(ID, new FieldType.StringType(), false)),
                schema(false, field(ID, new FieldType.BooleanType(), false))).compatible()).isFalse();
        assertThat(SchemaCompatibility.check(
                schema(false, field(COUNT, new FieldType.Int64Type(), false)),
                schema(false, field(COUNT, new FieldType.StringType(), false))).compatible()).isFalse();
        assertThat(SchemaCompatibility.check(
                schema(false, field(ID, new FieldType.BooleanType(), false)),
                schema(false, field(ID, new FieldType.Int64Type(), false))).compatible()).isFalse();
        assertThat(SchemaCompatibility.check(
                schema(false, field(ID, new FieldType.InstantType(), false)),
                schema(false, field(ID, new FieldType.StringType(), false))).compatible()).isFalse();
    }

    @Test
    void checkPreservesDiagnosticOrderForComplexCase() {
        Schema output = schema(false,
                field(ID, new FieldType.StringType(), true),
                field(new FieldId("name"), new FieldType.StringType(), false),
                field(COUNT, new FieldType.Int64Type(), false));
        Schema input = schema(false,
                field(ID, new FieldType.StringType(), false),
                field(new FieldId("name"), new FieldType.Int64Type(), false),
                field(new FieldId("missing"), new FieldType.StringType(), false),
                field(new FieldId("extra"), new FieldType.StringType(), false));

        SchemaCompatibilityResult result = SchemaCompatibility.check(output, input);

        assertThat(result.compatible()).isFalse();
        assertThat(result.diagnostics()).extracting(Diagnostic::message).containsExactly(
                "output field is nullable but input requires non-null: id",
                "incompatible type for field: name",
                "required input field missing in output: missing",
                "required input field missing in output: extra",
                "extra output field not allowed by closed input: count");
    }
}
