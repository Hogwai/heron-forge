package dev.hogwai.platform.spi.data;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldTest {

    private static final FieldId ID = new FieldId("id");
    private static final FieldId COUNT = new FieldId("count");
    public static final String COUNT_PASCAL_CASE = "Count";
    public static final String IDENTIFIER = "Identifier";

    // ------------------------------------------------------------------
    // Invariants
    // ------------------------------------------------------------------

    @Test
    void exposesComponents() {
        Field field = new Field(ID, IDENTIFIER, new FieldType.StringType(), false, Optional.empty());
        assertThat(field.id()).isEqualTo(ID);
        assertThat(field.displayName()).isEqualTo(IDENTIFIER);
        assertThat(field.type()).isEqualTo(new FieldType.StringType());
        assertThat(field.nullable()).isFalse();
        assertThat(field.defaultValue()).isEmpty();
    }

    @Test
    void rejectsNullId() {
        var stringType = new FieldType.StringType();
        assertThatThrownBy(() -> new Field(null, IDENTIFIER, stringType, false, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id must not be null");
    }

    @Test
    void rejectsNullDisplayName() {
        var stringType = new FieldType.StringType();
        assertThatThrownBy(() -> new Field(ID, null, stringType, false, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("displayName must not be null");
    }

    @Test
    void rejectsBlankDisplayName() {
        var stringType = new FieldType.StringType();
        assertThatThrownBy(() -> new Field(ID, " ", stringType, false, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName must not be blank");
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new Field(ID, IDENTIFIER, null, false, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type must not be null");
    }

    @Test
    void rejectsNullDefaultValue() {
        var stringType = new FieldType.StringType();
        assertThatThrownBy(() -> new Field(ID, IDENTIFIER, stringType, false, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("defaultValue must not be null");
    }

    @Test
    void rejectsDefaultNotAssignableToType() {
        Optional<Object> optionalX = Optional.of("x");
        var intType = new FieldType.Int64Type();
        assertThatThrownBy(() -> new Field(COUNT, COUNT_PASCAL_CASE, intType, false, optionalX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("default value is not assignable to field type: count");
    }

    // ------------------------------------------------------------------
    // Copies / Optional
    // ------------------------------------------------------------------

    @Test
    void preservesOptionalDefaultValue() {
        Field field = new Field(COUNT, COUNT_PASCAL_CASE, new FieldType.Int64Type(), false, Optional.of(5L));
        assertThat(field.defaultValue()).isPresent().contains(5L);
    }

    @Test
    void isRequiredWhenNonNullableAndNoDefault() {
        Field field = new Field(ID, IDENTIFIER, new FieldType.StringType(), false, Optional.empty());
        assertThat(field.isRequired()).isTrue();
    }

    @Test
    void notRequiredWhenNullable() {
        Field field = new Field(ID, IDENTIFIER, new FieldType.StringType(), true, Optional.empty());
        assertThat(field.isRequired()).isFalse();
    }

    @Test
    void notRequiredWhenDefaultPresent() {
        Field field = new Field(COUNT, COUNT_PASCAL_CASE, new FieldType.Int64Type(), false, Optional.of(5L));
        assertThat(field.isRequired()).isFalse();
    }

    // ------------------------------------------------------------------
    // Equality / hashCode / toString
    // ------------------------------------------------------------------

    @Test
    void equalityAndHashCode() {
        Field field = new Field(ID, IDENTIFIER, new FieldType.StringType(), false, Optional.empty());
        Field same = new Field(ID, IDENTIFIER, new FieldType.StringType(), false, Optional.empty());
        Field differentName = new Field(ID, "Other", new FieldType.StringType(), false, Optional.empty());
        Field differentType = new Field(ID, IDENTIFIER, new FieldType.BooleanType(), false, Optional.empty());
        Field differentNullable = new Field(ID, IDENTIFIER, new FieldType.StringType(), true, Optional.empty());
        Field differentDefault = new Field(COUNT, COUNT_PASCAL_CASE, new FieldType.Int64Type(), false, Optional.of(5L));

        assertThat(field).isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(differentName)
                .isNotEqualTo(differentType)
                .isNotEqualTo(differentNullable)
                .isNotEqualTo(differentDefault);
    }

    @Test
    void toStringDoesNotLeakDefaultValue() {
        Field empty = new Field(ID, IDENTIFIER, new FieldType.StringType(), false, Optional.empty());
        Field withDefault = new Field(COUNT, COUNT_PASCAL_CASE, new FieldType.Int64Type(), false, Optional.of(5L));

        assertThat(empty).hasToString(
                "Field[id=id, displayName=Identifier, type=StringType[], nullable=false, defaultValue=empty]");
        assertThat(withDefault.toString()).contains("defaultValue=present").doesNotContain("5");
    }
}