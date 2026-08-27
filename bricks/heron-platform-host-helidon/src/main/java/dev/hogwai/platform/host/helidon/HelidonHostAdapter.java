package dev.hogwai.platform.host.helidon;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.HostAdapter;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.host.HostException;
import io.helidon.webserver.WebServer;

import static dev.hogwai.platform.host.helidon.http.HttpHelper.writeHealth;

/**
 * Minimal Helidon transport adapter for the host API.
 */
@HeronService(value = HostAdapter.class, id = "host.helidon")
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class HelidonHostAdapter implements HostAdapter {

    public static final String HEALTH_LIVE_PATH = "/health/live";
    public static final String HEALTH_READY_PATH = "/health/ready";

    private final HelidonResponseWriter responseWriter;
    private WebServer server;

    /**
     * Creates a stopped adapter.
     */
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
        HelidonRequestInvoker invoker = new HelidonRequestInvoker(hostApplication, hostConfiguration, responseWriter);
        WebServer candidate = null;
        try {
            candidate = WebServer.builder()
                    .host(hostConfiguration.bindAddress())
                    .port(hostConfiguration.port())
                    .routing(routing -> {
                        routing.get(HEALTH_LIVE_PATH, (_, resp) ->
                                writeHealth(resp, true, ready()));
                        routing.get(HEALTH_READY_PATH, (_, resp) ->
                                writeHealth(resp, false, ready()));
                        for (EntrypointDescriptor descriptor : descriptors) {
                            routing.get(descriptor.path(), (request, response) ->
                                    invoker.invoke(descriptor, request, response));
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

    /**
     * Returns the effective listening port, or {@code -1} while stopped.
     */
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
        } catch (HostException _) {
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
                    || descriptor.path().equals(HEALTH_LIVE_PATH) || descriptor.path().equals(HEALTH_READY_PATH)) {
                throw new HostException("invalid or duplicate entrypoint descriptor");
            }
        }
    }

    private static void stopCandidate(WebServer candidate) {
        if (candidate != null) {
            try {
                candidate.stop();
            } catch (RuntimeException _) {
                // Preserve the original startup failure.
            }
        }
    }
}
