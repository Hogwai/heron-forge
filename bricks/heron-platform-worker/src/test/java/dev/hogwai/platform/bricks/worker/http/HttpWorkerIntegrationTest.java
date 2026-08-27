package dev.hogwai.platform.bricks.worker.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpWorkerIntegrationTest {

    private HttpServer server;
    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/echo", this::handleEcho);
        server.createContext("/fail", this::handleFail);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void invokePostsJsonAndReturnsMaterializedOutcome() throws Exception {
        HttpWorkerConfig config = new HttpWorkerConfig(baseUrl, Duration.ofSeconds(2),
                Duration.ofSeconds(5), Map.of());
        HttpWorker worker = new HttpWorker("test-http", config);

        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "echo", Map.of("orderId", "123"), Duration.ofSeconds(5), Map.of());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ExecutionOutcome> ref = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        worker.invoke(request, new WorkerCompletionCallback() {
            @Override
            public void onComplete(ExecutionOutcome outcome) {
                ref.set(outcome);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        assertThat(ref.get()).isNotNull();
        assertThat(ref.get().materialized()).isPresent();
    }

    @Test
    void invokeHandlesNon2xxAsMaterialized() throws Exception {
        HttpWorkerConfig config = new HttpWorkerConfig(baseUrl, Duration.ofSeconds(2),
                Duration.ofSeconds(5), Map.of());
        HttpWorker worker = new HttpWorker("test-http", config);

        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "fail", Map.of(), Duration.ofSeconds(5), Map.of());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ExecutionOutcome> ref = new AtomicReference<>();

        worker.invoke(request, outcome -> {
            ref.set(outcome);
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ref.get()).isNotNull();
    }

    private void handleEcho(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String requestBody = new String(body, StandardCharsets.UTF_8);
        Map<String, Object> response = Map.of("received", requestBody, "status", "ok");
        byte[] json = mapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json);
        }
    }

    private void handleFail(HttpExchange exchange) throws IOException {
        byte[] json = mapper.writeValueAsBytes(Map.of("error", "failure"));
        exchange.sendResponseHeaders(500, json.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json);
        }
    }
}
