package dev.hogwai.platform.runtime.config;

import dev.hogwai.platform.runtime.config.safe.SafeConfig;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable model of a single worker declaration.
 *
 * @param id        the worker identifier
 * @param transport the transport type (e.g., http)
 * @param config    the safe configuration values
 */
public record WorkerConfig(String id, String transport, Map<String, Object> config) {

    /**
     * Creates a worker configuration.
     *
     * @param id        the worker identifier
     * @param transport the transport type
     * @param config    the safe configuration values
     * @throws NullPointerException if any argument is null
     */
    public WorkerConfig {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(transport, "transport must not be null");
        if (transport.isBlank()) {
            throw new IllegalArgumentException("transport must not be blank");
        }
        config = SafeConfig.copy(config);
    }
}
