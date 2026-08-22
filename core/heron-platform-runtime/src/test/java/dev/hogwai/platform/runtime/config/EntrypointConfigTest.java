package dev.hogwai.platform.runtime.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntrypointConfigTest {

    @Test
    void exposesComponentsInContractOrder() {
        EntrypointConfig entrypoint = new EntrypointConfig("exceptions", "GET", "/exceptions", "detector");

        assertThat(entrypoint).extracting(
                        EntrypointConfig::id, EntrypointConfig::method,
                        EntrypointConfig::path, EntrypointConfig::target)
                .containsExactly("exceptions", "GET", "/exceptions", "detector");
    }

    @Test
    void recordEqualityAndHashCode() {
        EntrypointConfig first = new EntrypointConfig("exceptions", "GET", "/exceptions", "detector");
        EntrypointConfig equal = new EntrypointConfig("exceptions", "GET", "/exceptions", "detector");

        assertThat(first).isEqualTo(equal).hasSameHashCodeAs(equal);
    }

    @Test
    void rejectsNullAndBlankComponents() {
        assertThatThrownBy(() -> new EntrypointConfig(null, "GET", "/exceptions", "detector"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointConfig(" ", "GET", "/exceptions", "detector"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointConfig("exceptions", " ", "/exceptions", "detector"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointConfig("exceptions", "GET", " ", "detector"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointConfig("exceptions", "GET", "/exceptions", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
