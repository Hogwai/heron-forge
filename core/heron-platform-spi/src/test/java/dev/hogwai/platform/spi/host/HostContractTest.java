package dev.hogwai.platform.spi.host;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostContractTest {

    private static final CancellationSignal NOT_CANCELLED = () -> false;
    private static final Instant DEADLINE = Instant.parse("2026-08-20T12:00:00Z");
    public static final String ITEMS = "items";
    public static final String BIND_ADDRESS = "127.0.0.1";
    public static final String CHANGED = "changed";
    public static final String REQUEST = "request";
    public static final String ENTRYPOINT_PATH = "/entrypoint";
    public static final String ENTRYPOINT = "entrypoint";
    public static final String CORRELATION = "correlation";

    @Test
    void listsEntrypointsAndExecutesWithRequestMetadata() {
        EntrypointDescriptor descriptor = new EntrypointDescriptor("exceptions", "/exceptions");
        FakeApplication application = new FakeApplication(List.of(descriptor));
        InvocationRequest request = new InvocationRequest("exceptions", "request-1", "correlation-1", DEADLINE,
                NOT_CANCELLED);

        assertThat(application.entrypoints()).containsExactly(descriptor);
        assertThat(application.execute(request)).isSameAs(application.outcome);
        assertThat(application.lastRequest).isEqualTo(request);
    }

    @Test
    void executionOutcomeCarriesExactlyOneShape() {
        StructuredPayload payload = new StructuredPayload(Map.of("value", "row"));
        StreamingPayload stream = new StreamingPayload() {
            @Override
            public Optional<List<Map<String, Object>>> nextBatch() {
                return Optional.empty();
            }

            @Override
            public String schemaId() {
                return "s";
            }

            @Override
            public int schemaVersion() {
                return 1;
            }

            @Override
            public long deliveredRowCount() {
                return 0;
            }

            @Override
            public void close() {
                // No-op
            }
        };
        InvocationFailure failure = new InvocationFailure(FailureCode.PROVIDER, "provider failed");

        ExecutionOutcome materialized = ExecutionOutcome.materialized(payload);
        ExecutionOutcome streaming = ExecutionOutcome.streaming(stream);
        ExecutionOutcome failed = ExecutionOutcome.failure(failure);

        assertThat(materialized.isStreaming()).isFalse();
        assertThat(materialized.materialized()).contains(payload);
        assertThat(materialized.streaming()).isEmpty();
        assertThat(materialized.failure()).isEmpty();

        assertThat(streaming.isStreaming()).isTrue();
        assertThat(streaming.streaming()).contains(stream);
        assertThat(streaming.materialized()).isEmpty();
        assertThat(streaming.failure()).isEmpty();

        assertThat(failed.isStreaming()).isFalse();
        assertThat(failed.failure()).contains(failure);
        assertThat(failed.materialized()).isEmpty();
        assertThat(failed.streaming()).isEmpty();

        assertThatThrownBy(() -> ExecutionOutcome.materialized(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ExecutionOutcome.streaming(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ExecutionOutcome.failure(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings({"java:S5778", "unchecked"})
    void exposesSuccessAndFailureResults() {
        Map<String, Object> source = new LinkedHashMap<>();
        List<Object> sourceItems = new ArrayList<>();
        Map<String, Object> sourceItem = new LinkedHashMap<>();
        sourceItem.put("name", "first");
        sourceItems.add(sourceItem);
        source.put(ITEMS, sourceItems);
        source.put("enabled", true);
        StructuredPayload payload = new StructuredPayload(source);
        source.put(CHANGED, true);
        sourceItems.add(CHANGED);
        sourceItem.put(CHANGED, true);

        assertThat(new InvocationSuccess(payload).payload()).isSameAs(payload);
        assertThat(new InvocationFailure(FailureCode.PROVIDER, "provider failed"))
                .isEqualTo(new InvocationFailure(FailureCode.PROVIDER, "provider failed"));
        assertThat(payload.value().keySet()).containsExactly(ITEMS, "enabled");

        List<?> copiedItems = (List<?>) payload.value().get(ITEMS);
        assertThat(copiedItems).hasSize(1);
        assertThat(copiedItems.getFirst()).isEqualTo(Map.of("name", "first"));
        assertThatThrownBy(() -> payload.value().put(CHANGED, true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) payload.value().get(ITEMS)).add(CHANGED))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<String, Object>) copiedItems.getFirst()).put(CHANGED, true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void validatesStructuredPayloadAndEntrypoint() {
        Map invalidKey = new LinkedHashMap();
        invalidKey.put(1, "value");
        var map = Map.of("invalid", new Object());

        assertThatThrownBy(() -> new StructuredPayload(map))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructuredPayload(invalidKey))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructuredPayload(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor(null, ENTRYPOINT_PATH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor("", ENTRYPOINT_PATH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor(ENTRYPOINT, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor(ENTRYPOINT, ENTRYPOINT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor(ENTRYPOINT, "/entrypoint?format=json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor(ENTRYPOINT, "/entrypoint#details"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new EntrypointDescriptor(ENTRYPOINT, ENTRYPOINT_PATH)).isNotNull();
    }

    @Test
    void becomesReadyOnlyAfterStartAndClosesOnce() {
        FakeAdapter adapter = new FakeAdapter();
        FakeApplication application = new FakeApplication(List.of());
        HostConfiguration configuration = new HostConfiguration(BIND_ADDRESS, 0, Duration.ofSeconds(1));

        assertThat(adapter.ready()).isFalse();
        adapter.start(application, configuration);
        assertThat(adapter.ready()).isTrue();
        adapter.close();
        adapter.close();
        assertThat(adapter.closeCount).isEqualTo(1);
        assertThat(adapter.ready()).isFalse();
        application.close();
        application.close();
        assertThat(application.closeCount).isEqualTo(1);
    }

    @Test
    void validatesConfiguration() {
        Duration oneSecond = Duration.ofSeconds(1);
        Duration invalidDuration = Duration.ofSeconds(-1);
        assertThatThrownBy(() -> new HostConfiguration("", 8080, oneSecond))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration(BIND_ADDRESS, -1, oneSecond))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration(BIND_ADDRESS, 65536, oneSecond))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration(BIND_ADDRESS, 8080, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration(BIND_ADDRESS, 8080, invalidDuration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new HostConfiguration(BIND_ADDRESS, 0, oneSecond)).isNotNull();
        assertThat(new HostConfiguration(BIND_ADDRESS, 65535, oneSecond)).isNotNull();
    }

    @Test
    void validatesInvocationRequest() {
        assertThatThrownBy(() -> new InvocationRequest("", REQUEST, CORRELATION, DEADLINE, NOT_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest(ENTRYPOINT, "", CORRELATION, DEADLINE, NOT_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest(ENTRYPOINT, REQUEST, "", DEADLINE, NOT_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest(ENTRYPOINT, REQUEST, CORRELATION, null, NOT_CANCELLED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvocationRequest(ENTRYPOINT, REQUEST, CORRELATION, DEADLINE, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static final class FakeApplication implements HostApplication {
        private final List<EntrypointDescriptor> entrypoints;
        private final ExecutionOutcome outcome;
        private InvocationRequest lastRequest;
        private int closeCount;

        private FakeApplication(List<EntrypointDescriptor> entrypoints) {
            this.entrypoints = List.copyOf(entrypoints);
            this.outcome = ExecutionOutcome.failure(new InvocationFailure(FailureCode.INTERNAL, "not configured"));
        }

        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return entrypoints;
        }

        @Override
        public ExecutionOutcome execute(InvocationRequest request) {
            lastRequest = request;
            return outcome;
        }

        @Override
        public void close() {
            if (closeCount == 0) {
                closeCount++;
            }
        }
    }

    private static final class FakeAdapter implements HostAdapter {
        private boolean ready;
        private int closeCount;

        @Override
        public void start(HostApplication application, HostConfiguration configuration) {
            ready = true;
        }

        @Override
        public boolean ready() {
            return ready;
        }

        @Override
        public void stop() {
            ready = false;
        }

        @Override
        public void close() {
            if (closeCount == 0) {
                if (ready) {
                    stop();
                }
                closeCount++;
            }
        }
    }
}
