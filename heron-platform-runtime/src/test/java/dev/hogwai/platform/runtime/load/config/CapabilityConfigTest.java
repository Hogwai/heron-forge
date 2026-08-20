package dev.hogwai.platform.runtime.load.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.runtime.load.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.load.config.input.InputBindingConfig;
import dev.hogwai.platform.spi.CapabilityKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilityConfigTest {

    private static CapabilityConfig config(Map<String, Object> config) {
        return new CapabilityConfig("id", CapabilityKind.SOURCE, "acme", "1.2.3", config, List.of());
    }

    @Test
    void deepCopiesOriginalMapAndNestedCollections() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");
        List<Object> list = new ArrayList<>();
        list.add("a");
        Map<String, Object> config = new HashMap<>();
        config.put("nested", nested);
        config.put("list", list);

        CapabilityConfig cc = config(config);

        nested.put("key", "changed");
        list.add("b");
        config.put("new", "x");

        assertThat(cc.config()).containsEntry("nested", Map.of("key", "value"));
        assertThat(cc.config()).containsEntry("list", List.of("a"));
        assertThat(cc.config()).doesNotContainKey("new");
    }

    @Test
    void returnedViewIsImmutable() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");
        List<Object> list = new ArrayList<>();
        list.add("a");
        Map<String, Object> config = new HashMap<>();
        config.put("nested", nested);
        config.put("list", list);

        CapabilityConfig cc = config(config);

        Map<String, Object> configView = cc.config();
        Map<String, Object> nestedView = (Map<String, Object>) cc.config().get("nested");
        List<Object> listView = (List<Object>) cc.config().get("list");

        assertThatThrownBy(() -> configView.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> nestedView.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> listView.add("z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsCanonicalScalars() {
        Map<String, Object> config = new HashMap<>();
        config.put("s", "str");
        config.put("b", true);
        config.put("l", 42L);
        config.put("d", new BigDecimal("1.5"));
        config.put("n", null);

        CapabilityConfig cc = config(config);

        assertThat(cc.config()).containsEntry("s", "str");
        assertThat(cc.config()).containsEntry("b", true);
        assertThat(cc.config()).containsEntry("l", 42L);
        assertThat(cc.config()).containsEntry("d", new BigDecimal("1.5"));
        assertThat(cc.config()).containsKey("n");
        assertThat(cc.config().get("n")).isNull();
    }

    @Test
    void rejectsArbitraryJavaObject() {
        Map<String, Object> config = new HashMap<>();
        config.put("obj", new Object());

        assertThatThrownBy(() -> config(config)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonStringMapKey() {
        Map<Object, Object> config = new HashMap<>();
        config.put(1, "x");

        List<InputBindingConfig> noInputs = List.of();
        assertThatThrownBy(() -> new CapabilityConfig("id", CapabilityKind.SOURCE, "acme", "1.2.3",
                (Map) config, noInputs)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonCanonicalNumbers() {
        Map<String, Object> intConfig = new HashMap<>();
        intConfig.put("n", 42);
        assertThatThrownBy(() -> config(intConfig)).isInstanceOf(IllegalArgumentException.class);

        Map<String, Object> doubleConfig = new HashMap<>();
        doubleConfig.put("n", 1.5);
        assertThatThrownBy(() -> config(doubleConfig)).isInstanceOf(IllegalArgumentException.class);
    }
}
