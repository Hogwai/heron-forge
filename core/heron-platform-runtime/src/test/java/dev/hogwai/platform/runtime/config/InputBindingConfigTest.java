package dev.hogwai.platform.runtime.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputBindingConfigTest {

    @Test
    void exposesAllComponentsInContractOrder() {
        InputBindingConfig binding = new InputBindingConfig("in", "orders", "out");

        assertThat(binding).extracting(
                        InputBindingConfig::inputPort, InputBindingConfig::capability, InputBindingConfig::port)
                .containsExactly("in", "orders", "out");
    }

    @Test
    void recordEqualityAndHashCode() {
        InputBindingConfig a = new InputBindingConfig("in", "orders", "out");
        InputBindingConfig b = new InputBindingConfig("in", "orders", "out");
        InputBindingConfig differentInputPort = new InputBindingConfig("other", "orders", "out");
        InputBindingConfig differentPort = new InputBindingConfig("in", "orders", "other");
        InputBindingConfig differentCapability = new InputBindingConfig("in", "invoices", "out");

        assertThat(a)
                .isEqualTo(b)
                .hasSameHashCodeAs(b)
                .isNotEqualTo(differentInputPort)
                .isNotEqualTo(differentPort)
                .isNotEqualTo(differentCapability)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object());
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new InputBindingConfig(null, "orders", "out"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("inputPort must not be null");
        assertThatThrownBy(() -> new InputBindingConfig("in", null, "out"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("capability must not be null");
        assertThatThrownBy(() -> new InputBindingConfig("in", "orders", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("port must not be null");
    }

    @Test
    void isStoredAndExposedThroughCapabilityConfigCallSite() {
        InputBindingConfig binding = new InputBindingConfig("in", "orders", "out");
        CapabilityConfig config = new CapabilityConfig(
                "id", "acme", "1.2.3", Map.of(), List.of(binding));

        assertThat(config.inputs())
                .containsExactly(binding)
                .first()
                .isEqualTo(new InputBindingConfig("in", "orders", "out"));
    }
}
