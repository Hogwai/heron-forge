package dev.hogwai.platform.host.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.host.api.EntrypointDescriptor;
import dev.hogwai.platform.host.api.FailureCode;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.HostConfiguration;
import dev.hogwai.platform.host.api.HostException;
import dev.hogwai.platform.host.api.InvocationFailure;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationResult;
import dev.hogwai.platform.host.api.InvocationSuccess;
import dev.hogwai.platform.host.api.StructuredPayload;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HelidonHostAdapterTest {

    private static final HostConfiguration CONFIGURATION =
            new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(2));

    @Test
    void servesHealthDynamicRoutesAndGenericPayloads() throws Exception {
        RecordingApplication application = new RecordingApplication();
        HelidonHostAdapter adapter = new HelidonHostAdapter();
        try (HttpClient client = HttpClient.newHttpClient()) {
            adapter.start(application, CONFIGURATION);
            assertThat(adapter.ready()).isTrue();
            assertThat(adapter.port()).isPositive();

            HttpResponse<String> live = request(client, adapter.port(), "/health/live", Map.of());
            assertThat(live.statusCode()).isEqualTo(200);
            assertThat(live.body()).isEqualTo("{\"status\":\"live\"}");

            HttpResponse<String> ready = request(client, adapter.port(), "/health/ready", Map.of());
            assertThat(ready.statusCode()).isEqualTo(200);
            assertThat(ready.body()).isEqualTo("{\"status\":\"ready\"}");

            HttpResponse<String> result = request(client, adapter.port(), "/orders",
                    Map.of("X-Correlation-ID", "corr-7"));
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.headers().firstValue("X-Request-ID")).isPresent();
            assertThat(result.headers().firstValue("X-Correlation-ID")).contains("corr-7");
            assertThat(result.body()).isEqualTo("{\"message\":\"ok\",\"count\":2}");
            assertThat(application.request.get().entrypointId()).isEqualTo("orders");
            assertThat(application.request.get().deadline()).isAfter(Instant.now());
        } finally {
            adapter.close();
        }
    }

    @Test
    void exposesOnlyDeclaredGetRoutes() throws Exception {
        HelidonHostAdapter adapter = new HelidonHostAdapter();
        try (HttpClient client = HttpClient.newHttpClient()) {
            adapter.start(new RecordingApplication(), CONFIGURATION);
            assertThat(request(client, adapter.port(), "/unknown", Map.of()).statusCode()).isEqualTo(404);
            HttpRequest post = HttpRequest.newBuilder(uri(adapter.port(), "/orders"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertThat(client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        } finally {
            adapter.close();
        }
    }

    @Test
    void rejectsInvalidDescriptorsAndCleansUpBindFailure() throws Exception {
        assertThatThrownBy(() -> new HelidonHostAdapter().start(
                new RecordingApplication(List.of(
                        new EntrypointDescriptor("one", "/same"),
                        new EntrypointDescriptor("two", "/same"))), CONFIGURATION))
                .isInstanceOf(HostException.class);

        HelidonHostAdapter first = new HelidonHostAdapter();
        HelidonHostAdapter second = new HelidonHostAdapter();
        try {
            first.start(new RecordingApplication(), CONFIGURATION);
            HostConfiguration occupied = new HostConfiguration("127.0.0.1", first.port(), Duration.ofSeconds(1));
            assertThatThrownBy(() -> second.start(new RecordingApplication(), occupied))
                    .isInstanceOf(HostException.class);
            assertThat(second.ready()).isFalse();
            assertThat(second.port()).isEqualTo(-1);
            second.start(new RecordingApplication(), CONFIGURATION);
            assertThat(second.ready()).isTrue();
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void deadlineAndInterruptedThreadSetCancellationSignal() throws Exception {
        HelidonHostAdapter deadlineAdapter = new HelidonHostAdapter();
        HelidonHostAdapter interruptAdapter = new HelidonHostAdapter();
        try (HttpClient client = HttpClient.newHttpClient()) {
            deadlineAdapter.start(new DeadlineApplication(),
                    new HostConfiguration("127.0.0.1", 0, Duration.ofMillis(50)));
            assertThat(request(client, deadlineAdapter.port(), "/deadline", Map.of()).statusCode()).isEqualTo(408);

            interruptAdapter.start(new InterruptApplication(), CONFIGURATION);
            assertThat(request(client, interruptAdapter.port(), "/interrupt", Map.of()).statusCode()).isEqualTo(499);
        } finally {
            deadlineAdapter.close();
            interruptAdapter.close();
        }
    }

    @Test
    void shutdownIsIdempotentAndAStoppedServerIsNotReused() throws Exception {
        HelidonHostAdapter adapter = new HelidonHostAdapter();
        try {
            adapter.start(new RecordingApplication(), CONFIGURATION);
            adapter.stop();
            adapter.stop();
            adapter.close();
            adapter.close();
            assertThat(adapter.ready()).isFalse();
            assertThat(adapter.port()).isEqualTo(-1);

            adapter.start(new RecordingApplication(), CONFIGURATION);
            assertThat(adapter.ready()).isTrue();
            assertThat(adapter.port()).isPositive();
        } finally {
            adapter.close();
        }
    }

    @Test
    void hostSourcesDoNotCrossTheHostBoundary() throws IOException {
        for (String sourceName : List.of("HelidonHostAdapter.java", "HelidonResponseWriter.java")) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/dev/hogwai/platform/host/helidon", sourceName));
            assertThat(source).doesNotContain("dev.hogwai.platform.spi")
                    .doesNotContain("MaterializedDataSet")
                    .doesNotContain("SchemaRecord")
                    .doesNotContain("FieldId")
                    .doesNotContain("provider")
                    .doesNotContain("supply-chain");
        }
    }

    private static HttpResponse<String> request(HttpClient client, int port, String path,
            Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(port, path)).GET();
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static final class RecordingApplication implements HostApplication {
        private final List<EntrypointDescriptor> descriptors;
        private final AtomicReference<InvocationRequest> request = new AtomicReference<>();

        private RecordingApplication() {
            this(List.of(new EntrypointDescriptor("orders", "/orders")));
        }

        private RecordingApplication(List<EntrypointDescriptor> descriptors) {
            this.descriptors = descriptors;
        }

        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return descriptors;
        }

        @Override
        public InvocationResult invoke(InvocationRequest invocationRequest) {
            request.set(invocationRequest);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("message", "ok");
            value.put("count", 2L);
            return new InvocationSuccess(new StructuredPayload(value));
        }

        @Override
        public void close() {
        }
    }

    private static final class DeadlineApplication implements HostApplication {
        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return List.of(new EntrypointDescriptor("deadline", "/deadline"));
        }

        @Override
        public InvocationResult invoke(InvocationRequest request) {
            while (!request.cancellationSignal().isCancellationRequested()) {
                Thread.onSpinWait();
            }
            return new InvocationFailure(FailureCode.CANCELLATION_REQUESTED, "deadline observed");
        }

        @Override
        public void close() {
        }
    }

    private static final class InterruptApplication implements HostApplication {
        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return List.of(new EntrypointDescriptor("interrupt", "/interrupt"));
        }

        @Override
        public InvocationResult invoke(InvocationRequest request) {
            Thread.currentThread().interrupt();
            boolean cancelled = request.cancellationSignal().isCancellationRequested();
            Thread.interrupted();
            return cancelled
                    ? new InvocationFailure(FailureCode.CANCELLATION_REQUESTED, "interrupted")
                    : new InvocationFailure(FailureCode.INTERNAL, "signal failed");
        }

        @Override
        public void close() {
        }
    }
}
