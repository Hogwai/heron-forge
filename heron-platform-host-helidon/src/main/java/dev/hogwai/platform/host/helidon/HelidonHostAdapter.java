package dev.hogwai.platform.host.helidon;

import dev.hogwai.platform.host.api.CancellationSignal;
import dev.hogwai.platform.host.api.EntrypointDescriptor;
import dev.hogwai.platform.host.api.FailureCode;
import dev.hogwai.platform.host.api.HostAdapter;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.HostConfiguration;
import dev.hogwai.platform.host.api.HostException;
import dev.hogwai.platform.host.api.InvocationFailure;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationResult;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Minimal Helidon transport adapter for the host API. */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class HelidonHostAdapter implements HostAdapter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final byte[] LIVE_BODY = "{\"status\":\"live\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] READY_BODY = "{\"status\":\"ready\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_READY_BODY = "{\"status\":\"not-ready\"}".getBytes(StandardCharsets.UTF_8);

    private final HelidonResponseWriter responseWriter;
    private WebServer server;

    /** Creates a stopped adapter. */
    public HelidonHostAdapter() {
        responseWriter = new HelidonResponseWriter();
    }

    @Override
    public synchronized void start(HostApplication hostApplication, HostConfiguration hostConfiguration)
            throws HostException {
        Objects.requireNonNull(hostApplication, "application must not be null");
        Objects.requireNonNull(hostConfiguration, "configuration must not be null");
        if (server != null) {
            if (server.isRunning()) {
                return;
            }
            throw new HostException("host adapter is not available for startup");
        }

        List<EntrypointDescriptor> descriptors = hostApplication.entrypoints();
        validateEntrypoints(descriptors);
        WebServer candidate = null;
        try {
            candidate = WebServer.builder()
                    .host(hostConfiguration.bindAddress())
                    .port(hostConfiguration.port())
                    .routing(routing -> {
                        routing.get("/health/live", (request, response) -> writeHealth(response, true));
                        routing.get("/health/ready", (request, response) -> writeHealth(response, false));
                        for (EntrypointDescriptor descriptor : descriptors) {
                            routing.get(descriptor.path(), (request, response) ->
                                    invoke(hostApplication, hostConfiguration, descriptor, request, response));
                        }
                    })
                    .build();
            candidate.start();
            if (!candidate.isRunning()) {
                throw new HostException("host adapter failed to start");
            }
            server = candidate;
        } catch (HostException exception) {
            stopCandidate(candidate);
            throw exception;
        } catch (RuntimeException exception) {
            stopCandidate(candidate);
            throw new HostException("host adapter failed to start", exception);
        }
    }

    @Override
    public synchronized boolean ready() {
        return server != null && server.isRunning();
    }

    /** Returns the effective listening port, or {@code -1} while stopped. */
    public synchronized int port() {
        return server == null ? -1 : server.port();
    }

    @Override
    public synchronized void stop() throws HostException {
        WebServer serverToStop = server;
        server = null;
        if (serverToStop == null) {
            return;
        }
        try {
            serverToStop.stop();
        } catch (RuntimeException exception) {
            throw new HostException("host adapter failed to stop", exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            stop();
        } catch (HostException ignored) {
            // close() cannot report checked lifecycle failures; resources are detached.
        }
    }

    private static void validateEntrypoints(List<EntrypointDescriptor> descriptors) throws HostException {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new HostException("host application returned no entrypoints");
        }
        Set<String> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (EntrypointDescriptor descriptor : descriptors) {
            if (descriptor == null || !ids.add(descriptor.id()) || !paths.add(descriptor.path())
                    || descriptor.path().equals("/health/live") || descriptor.path().equals("/health/ready")) {
                throw new HostException("invalid or duplicate entrypoint descriptor");
            }
        }
    }

    private void invoke(HostApplication application, HostConfiguration configuration,
            EntrypointDescriptor descriptor, ServerRequest request, ServerResponse response) {
        String requestId = request.headers().first(HeaderNames.create(REQUEST_ID_HEADER))
                .orElseGet(HelidonHostAdapter::newId);
        String correlationId = request.headers().first(HeaderNames.create(CORRELATION_ID_HEADER)).orElse(requestId);
        if (requestId.isBlank() || correlationId.isBlank()
                || !validMetadata(requestId) || !validMetadata(correlationId)) {
            responseWriter.writeFailure(response, 400,
                    new InvocationFailure(FailureCode.INVALID_REQUEST, "invalid request metadata"));
            return;
        }
        response.header(REQUEST_ID_HEADER, requestId).header(CORRELATION_ID_HEADER, correlationId);

        Instant deadline = deadline(configuration.requestTimeout());
        CancellationSignal cancellation = () -> !Instant.now().isBefore(deadline)
                || Thread.currentThread().isInterrupted();
        InvocationResult result;
        try {
            result = application.invoke(new InvocationRequest(
                    descriptor.id(), requestId, correlationId, deadline, cancellation));
        } catch (RuntimeException exception) {
            result = new InvocationFailure(FailureCode.INTERNAL, "internal invocation failure");
        }
        if (result == null) {
            result = new InvocationFailure(FailureCode.INTERNAL, "internal invocation failure");
        }
        if (!Instant.now().isBefore(deadline)) {
            result = new InvocationFailure(FailureCode.DEADLINE_EXCEEDED, "deadline exceeded");
        }
        responseWriter.write(response, result);
    }

    private void writeHealth(ServerResponse response, boolean live) {
        boolean healthy = live || ready();
        response.status(healthy ? 200 : 503).header("content-type", JSON_CONTENT_TYPE)
                .send(live ? LIVE_BODY : (healthy ? READY_BODY : NOT_READY_BODY));
    }

    private static void stopCandidate(WebServer candidate) {
        if (candidate != null) {
            try {
                candidate.stop();
            } catch (RuntimeException ignored) {
                // Preserve the original startup failure.
            }
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static Instant deadline(Duration timeout) {
        try {
            return Instant.now().plus(timeout);
        } catch (ArithmeticException exception) {
            return Instant.MAX;
        }
    }

    private static boolean validMetadata(String value) {
        return value.length() <= 256 && value.codePoints().noneMatch(Character::isISOControl);
    }
}
