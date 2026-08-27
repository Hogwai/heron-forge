package dev.hogwai.platform.bricks.worker.http;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for HTTP worker.
 *
 * @param baseUrl        the base URL of the remote service
 * @param connectTimeout the connection timeout
 * @param requestTimeout the request timeout
 * @param defaultHeaders default headers to send with every request
 */
public record HttpWorkerConfig(
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        Map<String, String> defaultHeaders
) {

    /**
     * Validates the configuration.
     */
    public HttpWorkerConfig {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        Objects.requireNonNull(defaultHeaders, "defaultHeaders must not be null");
    }
}
