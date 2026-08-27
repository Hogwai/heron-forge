package dev.hogwai.platform.spi.invocation;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerInvocationRequestTest {

    @Test
    void exposesAllValues() {
        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "create",
                Map.of("key", "value"),
                Duration.ofSeconds(30),
                Map.of("header", "value")
        );
        assertThat(request.endpoint()).isEqualTo("create");
        assertThat(request.payload()).containsEntry("key", "value");
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(request.metadata()).containsEntry("header", "value");
    }

    @Test
    void rejectsBlankEndpoint() {
        assertThatThrownBy(() -> new WorkerInvocationRequest(
                "",
                Map.of(),
                Duration.ofSeconds(1),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new WorkerInvocationRequest(
                null,
                Map.of(),
                Duration.ofSeconds(1),
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new WorkerInvocationRequest(
                "endpoint",
                null,
                Duration.ofSeconds(1),
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new WorkerInvocationRequest(
                "endpoint",
                Map.of(),
                null,
                Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new WorkerInvocationRequest(
                "endpoint",
                Map.of(),
                Duration.ofSeconds(1),
                null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsZeroTimeout() {
        assertThatThrownBy(() -> new WorkerInvocationRequest(
                "endpoint",
                Map.of(),
                Duration.ZERO,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThatThrownBy(() -> new WorkerInvocationRequest(
                "endpoint",
                Map.of(),
                Duration.ofSeconds(-1),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
