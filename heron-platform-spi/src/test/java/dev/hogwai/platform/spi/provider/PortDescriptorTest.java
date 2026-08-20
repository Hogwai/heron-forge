package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.Schema;
import org.junit.jupiter.api.Test;

class PortDescriptorTest {

    @Test
    void acceptsValidDescriptor() {
        PortDescriptor descriptor = new PortDescriptor(new PortId("orders"), ProviderTestSupport.schema("s"), true);
        assertThat(descriptor.portId()).isEqualTo(new PortId("orders"));
        assertThat(descriptor.schema().identifier()).isEqualTo("s");
        assertThat(descriptor.required()).isTrue();
    }

    @Test
    void exposesRequiredFlag() {
        assertThat(new PortDescriptor(new PortId("a"), ProviderTestSupport.schema("s"), false).required()).isFalse();
    }

    @Test
    void rejectsNullPortId() {
        Schema schema = ProviderTestSupport.schema("s");
        assertThatThrownBy(() -> new PortDescriptor(null, schema, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSchema() {
        PortId portId = new PortId("a");
        assertThatThrownBy(() -> new PortDescriptor(portId, null, true))
                .isInstanceOf(NullPointerException.class);
    }
}
