package dev.hogwai.platform.spi.event;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final Instant TIMESTAMP = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void exposesAllValues() {
        Event event = new Event(
                "evt-1",
                "orders",
                "order.created",
                Map.of("orderId", "123"),
                TIMESTAMP,
                Map.of("source", "test")
        );
        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.topic()).isEqualTo("orders");
        assertThat(event.type()).isEqualTo("order.created");
        assertThat(event.payload()).containsEntry("orderId", "123");
        assertThat(event.timestamp()).isEqualTo(TIMESTAMP);
        assertThat(event.metadata()).containsEntry("source", "test");
    }

    @Test
    void createsWithFactoryMethod() {
        Event event = Event.of("orders", "order.created", Map.of("orderId", "123"));
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.topic()).isEqualTo("orders");
        assertThat(event.type()).isEqualTo("order.created");
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void rejectsBlankEventId() {
        assertThatThrownBy(() -> new Event(
                "",
                "orders",
                "order.created",
                Map.of(),
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankTopic() {
        assertThatThrownBy(() -> new Event(
                "evt-1",
                "",
                "order.created",
                Map.of(),
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(() -> new Event(
                "evt-1",
                "orders",
                "",
                Map.of(),
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new Event(
                null,
                "orders",
                "order.created",
                Map.of(),
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Event(
                "evt-1",
                null,
                "order.created",
                Map.of(),
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Event(
                "evt-1",
                "orders",
                null,
                Map.of(),
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Event(
                "evt-1",
                "orders",
                "order.created",
                null,
                TIMESTAMP,
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Event(
                "evt-1",
                "orders",
                "order.created",
                Map.of(),
                null,
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Event(
                "evt-1",
                "orders",
                "order.created",
                Map.of(),
                TIMESTAMP,
                null
        )).isInstanceOf(NullPointerException.class);
    }
}
