package dev.hogwai.platform.host.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HostContractTest {

    private static final CancellationSignal NOT_CANCELLED = () -> false;
    private static final Instant DEADLINE = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void listsEntrypointsAndInvokesWithRequestMetadata() {
        EntrypointDescriptor descriptor = new EntrypointDescriptor("exceptions", "/exceptions");
        FakeApplication application = new FakeApplication(List.of(descriptor));
        InvocationRequest request = new InvocationRequest("exceptions", "request-1", "correlation-1", DEADLINE,
                NOT_CANCELLED);

        assertThat(application.entrypoints()).containsExactly(descriptor);
        assertThat(application.invoke(request)).isEqualTo(application.success);
        assertThat(application.lastRequest).isEqualTo(request);
    }

    @SuppressWarnings("unchecked")
    @Test
    void exposesSuccessAndFailureResults() {
        Map<String, Object> source = new LinkedHashMap<>();
        List<Object> sourceItems = new ArrayList<>();
        Map<String, Object> sourceItem = new LinkedHashMap<>();
        sourceItem.put("name", "first");
        sourceItems.add(sourceItem);
        source.put("items", sourceItems);
        source.put("enabled", true);
        StructuredPayload payload = new StructuredPayload(source);
        source.put("changed", true);
        sourceItems.add("changed");
        sourceItem.put("changed", true);

        assertThat(new InvocationSuccess(payload).payload()).isSameAs(payload);
        assertThat(new InvocationFailure(FailureCode.PROVIDER, "provider failed"))
                .isEqualTo(new InvocationFailure(FailureCode.PROVIDER, "provider failed"));
        assertThat(payload.value().keySet()).containsExactly("items", "enabled");
        List<?> copiedItems = (List<?>) payload.value().get("items");
        assertThat(copiedItems).hasSize(1);
        assertThat(copiedItems.get(0)).isEqualTo(Map.of("name", "first"));
        assertThatThrownBy(() -> payload.value().put("changed", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) payload.value().get("items")).add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<String, Object>) copiedItems.get(0)).put("changed", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void validatesStructuredPayloadAndEntrypoint() {
        Map invalidKey = new LinkedHashMap();
        invalidKey.put(1, "value");

        assertThatThrownBy(() -> new StructuredPayload(Map.of("invalid", new Object())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructuredPayload(invalidKey))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructuredPayload(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor(null, "/entrypoint"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor("", "/entrypoint"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor("entrypoint", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor("entrypoint", "entrypoint"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor("entrypoint", "/entrypoint?format=json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntrypointDescriptor("entrypoint", "/entrypoint#details"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new EntrypointDescriptor("entrypoint", "/entrypoint")).isNotNull();
    }

    @Test
    void becomesReadyOnlyAfterStartAndClosesOnce() throws HostException {
        FakeAdapter adapter = new FakeAdapter();
        FakeApplication application = new FakeApplication(List.of());
        HostConfiguration configuration = new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(1));

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
        assertThatThrownBy(() -> new HostConfiguration("", 8080, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration("127.0.0.1", -1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration("127.0.0.1", 65536, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration("127.0.0.1", 8080, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostConfiguration("127.0.0.1", 8080, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(1))).isNotNull();
        assertThat(new HostConfiguration("127.0.0.1", 65535, Duration.ofSeconds(1))).isNotNull();
    }

    @Test
    void validatesInvocationRequest() {
        assertThatThrownBy(() -> new InvocationRequest("", "request", "correlation", DEADLINE, NOT_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest("entrypoint", "", "correlation", DEADLINE, NOT_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest("entrypoint", "request", "", DEADLINE, NOT_CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest("entrypoint", "request", "correlation", null, NOT_CANCELLED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvocationRequest("entrypoint", "request", "correlation", DEADLINE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hostApiSourcesDoNotImportFrameworkTypes() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> sources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::readSource)
                    .toList();
            assertThat(sources).allSatisfy(source -> assertThat(source)
                    .doesNotContain("io.vertx")
                    .doesNotContain("jakarta.servlet")
                    .doesNotContain("org.springframework")
                    .doesNotContain("io.helidon")
                    .doesNotContain("io.quarkus")
                    .doesNotContain("io.micronaut"));
        }
    }

    private String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source", exception);
        }
    }

    private static final class FakeApplication implements HostApplication {
        private final List<EntrypointDescriptor> entrypoints;
        private final InvocationResult success;
        private InvocationRequest lastRequest;
        private int closeCount;

        private FakeApplication(List<EntrypointDescriptor> entrypoints) {
            this.entrypoints = List.copyOf(entrypoints);
            this.success = new InvocationFailure(FailureCode.INTERNAL, "not configured");
        }

        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return entrypoints;
        }

        @Override
        public InvocationResult invoke(InvocationRequest request) {
            lastRequest = request;
            return success;
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
