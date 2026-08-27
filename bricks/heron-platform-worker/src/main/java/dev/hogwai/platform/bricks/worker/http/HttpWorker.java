package dev.hogwai.platform.bricks.worker.http;

import java.util.List;
import java.util.Map;

import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.StructuredPayload;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;

/**
 * HTTP worker using Helidon WebClient.
 *
 * <p>Uses Virtual Threads (Java 21+) for non-blocking execution.
 * Each invocation runs on a dedicated Virtual Thread.
 */
public class HttpWorker implements AsyncWorker {

    private final String id;
    private final HttpWorkerConfig config;
    private final WebClient webClient;

    /**
     * Creates an HTTP worker.
     *
     * @param id     the worker identifier
     * @param config the HTTP configuration
     */
    public HttpWorker(String id, HttpWorkerConfig config) {
        this.id = id;
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUri(config.baseUrl())
                .connectTimeout(config.connectTimeout())
                .addMediaSupport(JacksonSupport.create())
                .build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public HostApplication host() {
        return null;
    }

    @Override
    public List<EntrypointDescriptor> endpoints() {
        return List.of();
    }

    @Override
    public void invoke(WorkerInvocationRequest request, WorkerCompletionCallback callback) {
        Thread.startVirtualThread(() -> {
            try {
                HttpClientResponse response = webClient.post()
                        .path(request.endpoint())
                        .submit(request.payload());

                ExecutionOutcome outcome = toOutcome(response);
                callback.onComplete(outcome);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        });
    }

    private ExecutionOutcome toOutcome(HttpClientResponse response) {
        int status = response.status().code();
        String body = response.as(String.class);
        StructuredPayload payload = new StructuredPayload(Map.of("statusCode", status, "body", body));
        return ExecutionOutcome.materialized(payload);
    }
}
