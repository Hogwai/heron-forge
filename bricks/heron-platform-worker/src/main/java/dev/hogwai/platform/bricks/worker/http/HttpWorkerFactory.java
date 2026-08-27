package dev.hogwai.platform.bricks.worker.http;

import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Factory for HTTP workers.
 */
@HeronService(value = WorkerFactory.class, id = "worker.http")
public final class HttpWorkerFactory implements WorkerFactory {

    @Override
    public String transport() {
        return "http";
    }

    @Override
    public AsyncWorker create(String id, Map<String, Object> config) {
        Object baseUrl = config.get("baseUrl");
        if (!(baseUrl instanceof String url) || url.isBlank()) {
            throw new IllegalArgumentException("http worker requires non-blank baseUrl");
        }
        Duration connectTimeout = parseDuration(config.get("connectTimeout"), Duration.ofSeconds(5));
        Duration requestTimeout = parseDuration(config.get("requestTimeout"), Duration.ofSeconds(30));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = config.get("defaultHeaders") instanceof Map<?, ?> m
                ? (Map<String, String>) m : Map.of();
        HttpWorkerConfig httpConfig = new HttpWorkerConfig(url, connectTimeout, requestTimeout, headers);
        return new HttpWorker(id, httpConfig);
    }

    private Duration parseDuration(Object value, Duration fallback) {
        if (value instanceof String s) {
            return Duration.parse(s);
        }
        if (value instanceof Number n) {
            return Duration.ofMillis(n.longValue());
        }
        return fallback;
    }
}
