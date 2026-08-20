package dev.hogwai.platform.spi.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SchemaTest {

    private static final FieldId ID = new FieldId("id");
    private static final FieldId COUNT = new FieldId("count");

    // ------------------------------------------------------------------
    // FieldId
    // ------------------------------------------------------------------

    @Test
    void fieldIdAcceptsValidValue() {
        FieldId id = new FieldId("orders");
        assertThat(id.value()).isEqualTo("orders");
        assertThat(id).hasToString("orders");
        assertThat(new FieldId("orders")).isEqualTo(new FieldId("orders"));
    }

    @Test
    void fieldIdRejectsNullBlankAndWhitespace() {
        assertThatThrownBy(() -> new FieldId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldId("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldId("my field")).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // EnumDefinition
    // ------------------------------------------------------------------

    @Test
    void enumDefinitionAcceptsValidSymbols() {
        EnumDefinition def = new EnumDefinition("status", List.of("open", "closed"));
        assertThat(def.identifier()).isEqualTo("status");
        assertThat(def.symbols()).containsExactly("open", "closed");
    }

    @Test
    void enumDefinitionRejectsNullAndBlankIdentifier() {
        List<String> symbols = List.of("a");
        assertThatThrownBy(() -> new EnumDefinition(null, symbols)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EnumDefinition("", symbols)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumDefinitionRejectsDuplicateSymbols() {
        List<String> duplicateSymbols = List.of("a", "a");
        assertThatThrownBy(() -> new EnumDefinition("s", duplicateSymbols))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumDefinitionRejectsNullAndBlankSymbols() {
        List<String> withNullSymbol = new ArrayList<>();
        withNullSymbol.add("a");
        withNullSymbol.add(null);
        assertThatThrownBy(() -> new EnumDefinition("s", withNullSymbol)).isInstanceOf(NullPointerException.class);
        List<String> blankSymbol = List.of("a", "");
        assertThatThrownBy(() -> new EnumDefinition("s", blankSymbol)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumDefinitionPreservesOrderAndIsImmutable() {
        EnumDefinition def = new EnumDefinition("status", List.of("open", "closed", "pending"));
        assertThat(def.symbols()).containsExactly("open", "closed", "pending");
        List<String> symbolsView = def.symbols();
        assertThatThrownBy(() -> symbolsView.add("extra")).isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------------------
    // FieldType
    // ------------------------------------------------------------------

    @Test
    void fieldTypeAcceptsOnlyAssignableValues() {
        assertThat(new FieldType.StringType().accepts("x")).isTrue();
        assertThat(new FieldType.StringType().accepts(1L)).isFalse();
        assertThat(new FieldType.Int64Type().accepts(1L)).isTrue();
        assertThat(new FieldType.Int64Type().accepts(1)).isFalse();
        assertThat(new FieldType.BooleanType().accepts(true)).isTrue();
        assertThat(new FieldType.BooleanType().accepts("true")).isFalse();
        assertThat(new FieldType.DecimalType().accepts(new BigDecimal("1.5"))).isTrue();
        assertThat(new FieldType.DecimalType().accepts(1L)).isFalse();
        assertThat(new FieldType.InstantType().accepts(Instant.now())).isTrue();
        assertThat(new FieldType.InstantType().accepts("now")).isFalse();
    }

    @Test
    void enumTypeAcceptsOnlyDeclaredSymbols() {
        FieldType type = new FieldType.EnumType(new EnumDefinition("status", List.of("open", "closed")));
        assertThat(type.accepts("open")).isTrue();
        assertThat(type.accepts("closed")).isTrue();
        assertThat(type.accepts("pending")).isFalse();
        assertThat(type.accepts(1L)).isFalse();
    }

    // ------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------

    @Test
    void fieldAcceptsValidValues() {
        Field field = new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty());
        assertThat(field.id()).isEqualTo(ID);
        assertThat(field.displayName()).isEqualTo("Identifier");
        assertThat(field.type()).isEqualTo(new FieldType.StringType());
        assertThat(field.nullable()).isFalse();
        assertThat(field.defaultValue()).isEmpty();
        assertThat(field.isRequired()).isTrue();
    }

    @Test
    void fieldRejectsBlankDisplayName() {
        FieldType stringType = new FieldType.StringType();
        Optional<Object> noDefault = Optional.empty();
        assertThatThrownBy(() -> new Field(ID, "", stringType, false, noDefault))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldRejectsDefaultNotAssignableToType() {
        FieldType int64Type = new FieldType.Int64Type();
        Optional<Object> badDefault = Optional.of("not-a-long");
        assertThatThrownBy(() -> new Field(COUNT, "Count", int64Type, false, badDefault))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldWithDefaultIsNotRequired() {
        Field field = new Field(COUNT, "Count", new FieldType.Int64Type(), false, Optional.of(5L));
        assertThat(field.isRequired()).isFalse();
    }
}