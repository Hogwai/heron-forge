package dev.hogwai.platform.spi.provider;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationSchemaTest {

    public static final String TIMEOUT = "timeout";

    @Test
    void acceptsValidSchema() {
        ConfigurationSchema schema = new ConfigurationSchema(
                Set.of("host", "port", TIMEOUT),
                Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING,
                        "port", ConfigurationSchema.ScalarKind.INTEGER),
                Map.of(TIMEOUT, "use timeoutMs instead"));
        assertThat(schema.allowedFields()).containsExactlyInAnyOrder("host", "port", TIMEOUT);
        assertThat(schema.requiredFields()).containsExactly("host");
        assertThat(schema.fieldKinds()).containsEntry("host", ConfigurationSchema.ScalarKind.STRING);
        assertThat(schema.deprecations()).containsEntry(TIMEOUT, "use timeoutMs instead");
    }

    @Test
    void rejectsNullCollections() {
        Set<String> emptyFields = Set.of();
        Map<String, ConfigurationSchema.ScalarKind> emptyKinds = Map.of();
        Map<String, String> emptyDeprecations = Map.of();

        assertThatThrownBy(() -> new ConfigurationSchema(null, emptyFields, emptyKinds, emptyDeprecations))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfigurationSchema(emptyFields, null, emptyKinds, emptyDeprecations))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfigurationSchema(emptyFields, emptyFields, null, emptyDeprecations))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfigurationSchema(emptyFields, emptyFields, emptyKinds, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankAndNullNames() {
        Set<String> emptyFields = Set.of();
        Map<String, ConfigurationSchema.ScalarKind> emptyKinds = Map.of();
        Map<String, String> emptyDeprecations = Map.of();

        Set<String> blankField = Set.of("");
        assertThatThrownBy(() -> new ConfigurationSchema(blankField, emptyFields, emptyKinds, emptyDeprecations))
                .isInstanceOf(IllegalArgumentException.class);
        Set<String> withNull = new HashSet<>();
        withNull.add("a");
        withNull.add(null);
        assertThatThrownBy(() -> new ConfigurationSchema(withNull, emptyFields, emptyKinds, emptyDeprecations))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsRequiredFieldNotAllowed() {
        Set<String> allowed = Set.of("a");
        Set<String> required = Set.of("b");
        Map<String, ConfigurationSchema.ScalarKind> emptyKinds = Map.of();
        Map<String, String> emptyDeprecations = Map.of();
        assertThatThrownBy(() -> new ConfigurationSchema(allowed, required, emptyKinds, emptyDeprecations))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsKindForUnknownField() {
        Set<String> allowed = Set.of("a");
        Set<String> emptyFields = Set.of();
        Map<String, ConfigurationSchema.ScalarKind> kindForUnknown =
                Map.of("b", ConfigurationSchema.ScalarKind.STRING);
        Map<String, String> emptyDeprecations = Map.of();
        assertThatThrownBy(() -> new ConfigurationSchema(allowed, emptyFields, kindForUnknown, emptyDeprecations))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDeprecationForUnknownField() {
        Set<String> allowed = Set.of("a");
        Set<String> emptyFields = Set.of();
        Map<String, ConfigurationSchema.ScalarKind> emptyKinds = Map.of();
        Map<String, String> deprecationForUnknown = Map.of("b", "msg");
        assertThatThrownBy(() -> new ConfigurationSchema(allowed, emptyFields, emptyKinds, deprecationForUnknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankDeprecationMessage() {
        Set<String> allowed = Set.of("a");
        Set<String> emptyFields = Set.of();
        Map<String, ConfigurationSchema.ScalarKind> emptyKinds = Map.of();
        Map<String, String> blankDeprecation = Map.of("a", " ");
        assertThatThrownBy(() -> new ConfigurationSchema(allowed, emptyFields, emptyKinds, blankDeprecation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void collectionsAreImmutable() {
        ConfigurationSchema schema = new ConfigurationSchema(Set.of("a"), Set.of(), Map.of(), Map.of());
        Set<String> allowedView = schema.allowedFields();
        Set<String> requiredView = schema.requiredFields();
        Map<String, ConfigurationSchema.ScalarKind> kindsView = schema.fieldKinds();
        Map<String, String> deprecationsView = schema.deprecations();
        assertThatThrownBy(() -> allowedView.add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> requiredView.add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> kindsView.put("x", ConfigurationSchema.ScalarKind.STRING))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> deprecationsView.put("x", "m"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorCopiesCollectionsDefensively() {
        Set<String> allowed = new HashSet<>(Set.of("a"));
        Set<String> required = new HashSet<>();
        Map<String, ConfigurationSchema.ScalarKind> kinds = new HashMap<>();
        kinds.put("a", ConfigurationSchema.ScalarKind.STRING);
        Map<String, String> deprecations = new HashMap<>();
        ConfigurationSchema schema = new ConfigurationSchema(allowed, required, kinds, deprecations);

        allowed.add("b");
        required.add("b");
        kinds.put("b", ConfigurationSchema.ScalarKind.INTEGER);
        deprecations.put("a", "msg");

        assertThat(schema.allowedFields()).containsExactly("a");
        assertThat(schema.requiredFields()).isEmpty();
        assertThat(schema.fieldKinds()).containsExactlyEntriesOf(
                Map.of("a", ConfigurationSchema.ScalarKind.STRING));
        assertThat(schema.deprecations()).isEmpty();
    }

    @Test
    void equalityHashCodeAndToString() {
        ConfigurationSchema schema = new ConfigurationSchema(
                Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
        ConfigurationSchema same = new ConfigurationSchema(
                Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
        ConfigurationSchema differentAllowed = new ConfigurationSchema(
                Set.of("host", "port"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
        ConfigurationSchema differentRequired = new ConfigurationSchema(
                Set.of("host"), Set.of(),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
        ConfigurationSchema differentKinds = new ConfigurationSchema(
                Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.INTEGER), Map.of());
        ConfigurationSchema differentDeprecations = new ConfigurationSchema(
                Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of("host", "use other"));

        assertThat(schema).isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(differentAllowed)
                .isNotEqualTo(differentRequired)
                .isNotEqualTo(differentKinds)
                .isNotEqualTo(differentDeprecations)
                .hasToString("ConfigurationSchema[allowedFields=[host], requiredFields=[host], fieldKinds={host=STRING}, deprecations={}]");
    }
}