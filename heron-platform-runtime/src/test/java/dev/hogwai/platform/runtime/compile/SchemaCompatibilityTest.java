package dev.hogwai.platform.runtime.compile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.data.EnumDefinition;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SchemaCompatibilityTest {

    private static final FieldId ID = new FieldId("id");
    private static final FieldId NAME = new FieldId("name");
    private static final FieldId COUNT = new FieldId("count");

    private static Field field(FieldId id, FieldType type, boolean nullable) {
        return new Field(id, id.value(), type, nullable, Optional.empty());
    }

    private static Schema schema(boolean open, Field... fields) {
        return new Schema("s", 1, List.of(fields), open);
    }

    @Test
    void identicalSchemasAreCompatible() {
        Schema a = schema(false, field(ID, new FieldType.StringType(), false));
        SchemaCompatibilityResult result = SchemaCompatibility.check(a, a);
        assertThat(result.compatible()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void missingRequiredInputFieldIsIncompatible() {
        Schema output = schema(false, field(ID, new FieldType.StringType(), false));
        Schema input = schema(false, field(ID, new FieldType.StringType(), false),
                field(NAME, new FieldType.StringType(), false));
        SchemaCompatibilityResult result = SchemaCompatibility.check(output, input);
        assertThat(result.compatible()).isFalse();
        assertThat(result.diagnostics()).allMatch(d -> d.code() == PlatformErrorCode.SCHEMA_INCOMPATIBLE);
    }

    @Test
    void missingOptionalInputFieldIsCompatible() {
        Schema output = schema(false, field(ID, new FieldType.StringType(), false));
        Schema input = schema(false, field(ID, new FieldType.StringType(), false),
                field(NAME, new FieldType.StringType(), true));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void incompatibleTypesAreRejectedWithoutNumericWidening() {
        Schema output = schema(false, field(COUNT, new FieldType.Int64Type(), false));
        Schema input = schema(false, field(COUNT, new FieldType.DecimalType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isFalse();
    }

    @Test
    void nullabilityMustBeRespected() {
        Schema output = schema(false, field(ID, new FieldType.StringType(), true));
        Schema input = schema(false, field(ID, new FieldType.StringType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isFalse();
    }

    @Test
    void extraOutputFieldRejectedWhenInputClosed() {
        Schema output = schema(false, field(ID, new FieldType.StringType(), false),
                field(NAME, new FieldType.StringType(), false));
        Schema input = schema(false, field(ID, new FieldType.StringType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isFalse();
    }

    @Test
    void extraOutputFieldAllowedWhenInputOpen() {
        Schema output = schema(false, field(ID, new FieldType.StringType(), false),
                field(NAME, new FieldType.StringType(), false));
        Schema input = schema(true, field(ID, new FieldType.StringType(), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void enumSymbolAdditionOnOutputIsIncompatible() {
        Schema output = schema(false, field(ID,
                new FieldType.EnumType(new EnumDefinition("status", List.of("open", "closed", "pending"))), false));
        Schema input = schema(false, field(ID,
                new FieldType.EnumType(new EnumDefinition("status", List.of("open", "closed"))), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isFalse();
    }

    @Test
    void enumSymbolSubsetOnOutputIsCompatible() {
        Schema output = schema(false, field(ID,
                new FieldType.EnumType(new EnumDefinition("status", List.of("open"))), false));
        Schema input = schema(false, field(ID,
                new FieldType.EnumType(new EnumDefinition("status", List.of("open", "closed"))), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isTrue();
    }

    @Test
    void enumWithDifferentIdentifiersIsIncompatibleEvenWithSameSymbols() {
        Schema output = schema(false, field(ID,
                new FieldType.EnumType(new EnumDefinition("status_a", List.of("open", "closed"))), false));
        Schema input = schema(false, field(ID,
                new FieldType.EnumType(new EnumDefinition("status_b", List.of("open", "closed"))), false));
        assertThat(SchemaCompatibility.check(output, input).compatible()).isFalse();
    }

    @Test
    void checkTransitiveReturnsAllFailuresWithEdgePath() {
        Schema a = schema(false, field(ID, new FieldType.StringType(), false));
        Schema b = schema(false, field(ID, new FieldType.StringType(), false),
                field(NAME, new FieldType.StringType(), false));
        Schema c = schema(false, field(ID, new FieldType.StringType(), false),
                field(NAME, new FieldType.StringType(), false),
                field(COUNT, new FieldType.Int64Type(), false));
        SchemaCompatibilityResult result = SchemaCompatibility.checkTransitive(List.of(
                new SchemaCompatibility.SchemaEdge("/a", a, b),
                new SchemaCompatibility.SchemaEdge("/b", b, c)));
        assertThat(result.compatible()).isFalse();
        assertThat(result.diagnostics()).hasSize(2);
        assertThat(result.diagnostics()).allMatch(d -> d.code() == PlatformErrorCode.SCHEMA_INCOMPATIBLE);
        assertThat(result.diagnostics()).extracting(d -> d.path()).containsExactly("/a", "/b");
    }

    @Test
    void checkTransitiveWithAllCompatibleEdgesIsCompatible() {
        Schema a = schema(false, field(ID, new FieldType.StringType(), false));
        Schema b = schema(false, field(ID, new FieldType.StringType(), false));
        SchemaCompatibilityResult result = SchemaCompatibility.checkTransitive(List.of(
                new SchemaCompatibility.SchemaEdge("/a", a, b)));
        assertThat(result.compatible()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }
}
