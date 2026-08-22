package dev.hogwai.platform.host.helidon;

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

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.FailureCode;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.host.HostException;
import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.host.InvocationResult;
import dev.hogwai.platform.spi.host.InvocationSuccess;
import dev.hogwai.platform.spi.host.StructuredPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelidonHostAdapterTest {

    public static final String BIND_ADDRESS = "127.0.0.1";
    private static final HostConfiguration CONFIGURATION =
            new HostConfiguration(BIND_ADDRESS, 0, Duration.ofSeconds(2));
    public static final String ORDERS = "/orders";

    @Test
    void servesHealthDynamicRoutesAndGenericPayloads() throws Exception {
        RecordingApplication application = new RecordingApplication();
        try (HelidonHostAdapter adapter = new HelidonHostAdapter();
             HttpClient client = HttpClient.newHttpClient()) {
            adapter.start(application, CONFIGURATION);
            assertThat(adapter.ready()).isTrue();
            assertThat(adapter.port()).isPositive();

            HttpResponse<String> live = request(client, adapter.port(), "/health/live", Map.of());
            assertThat(live.statusCode()).isEqualTo(200);
            assertThat(live.body()).isEqualTo("{\"status\":\"live\"}");

            HttpResponse<String> ready = request(client, adapter.port(), "/health/ready", Map.of());
            assertThat(ready.statusCode()).isEqualTo(200);
            assertThat(ready.body()).isEqualTo("{\"status\":\"ready\"}");

            HttpResponse<String> result = request(client, adapter.port(), ORDERS,
                    Map.of("X-Correlation-ID", "corr-7"));
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.headers().firstValue("X-Request-ID")).isPresent();
            assertThat(result.headers().firstValue("X-Correlation-ID")).contains("corr-7");
            assertThat(result.body()).isEqualTo("{\"message\":\"ok\",\"count\":2}");
            assertThat(application.request.get().entrypointId()).isEqualTo("orders");
            assertThat(application.request.get().deadline()).isAfter(Instant.now());
        }
    }

    @Test
    void exposesOnlyDeclaredGetRoutes() throws Exception {
        try (HelidonHostAdapter adapter = new HelidonHostAdapter();
             HttpClient client = HttpClient.newHttpClient()) {
            adapter.start(new RecordingApplication(), CONFIGURATION);
            assertThat(request(client, adapter.port(), "/unknown", Map.of()).statusCode()).isEqualTo(404);
            HttpRequest post = HttpRequest.newBuilder(uri(adapter.port(), ORDERS))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertThat(client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void rejectsInvalidDescriptorsAndCleansUpBindFailure() throws Exception {
        try (HelidonHostAdapter adapter = new HelidonHostAdapter()) {
            assertThatThrownBy(() -> adapter.start(
                    new RecordingApplication(List.of(
                            new EntrypointDescriptor("one", "/same"),
                            new EntrypointDescriptor("two", "/same"))), CONFIGURATION))
                    .isInstanceOf(HostException.class);
        }

        try (HelidonHostAdapter first = new HelidonHostAdapter();
             HelidonHostAdapter second = new HelidonHostAdapter()) {
            first.start(new RecordingApplication(), CONFIGURATION);
            HostConfiguration occupied = new HostConfiguration(BIND_ADDRESS, first.port(), Duration.ofSeconds(1));
            assertThatThrownBy(() -> second.start(new RecordingApplication(), occupied))
                    .isInstanceOf(HostException.class);
            assertThat(second.ready()).isFalse();
            assertThat(second.port()).isEqualTo(-1);
            second.start(new RecordingApplication(), CONFIGURATION);
            assertThat(second.ready()).isTrue();
        }
    }

    @Test
    void deadlineAndInterruptedThreadSetCancellationSignal() throws Exception {
        try (HelidonHostAdapter deadlineAdapter = new HelidonHostAdapter();
             HelidonHostAdapter interruptAdapter = new HelidonHostAdapter();
             HttpClient client = HttpClient.newHttpClient()) {
            deadlineAdapter.start(new DeadlineApplication(),
                    new HostConfiguration(BIND_ADDRESS, 0, Duration.ofMillis(50)));
            assertThat(request(client, deadlineAdapter.port(), "/deadline", Map.of()).statusCode()).isEqualTo(408);

            interruptAdapter.start(new InterruptApplication(), CONFIGURATION);
            assertThat(request(client, interruptAdapter.port(), "/interrupt", Map.of()).statusCode()).isEqualTo(499);
        }
    }

    @Test
    void shutdownIsIdempotentAndAStoppedServerIsNotReused() throws Exception {
        try (HelidonHostAdapter adapter = new HelidonHostAdapter()) {
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
            this(List.of(new EntrypointDescriptor("orders", ORDERS)));
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
            // No-op
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
            // No-op
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
            // No-op
        }
    }
}
