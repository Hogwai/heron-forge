package dev.hogwai.platform.runtime.config;

import java.util.Objects;

/**
 * Immutable model of an application HTTP entrypoint.
 *
 * <p>The runtime configuration mapper currently accepts only {@code GET}
 * methods. The method remains a string so this model does not depend on an
 * HTTP or web framework.
 */
public record EntrypointConfig(String id, String method, String path, String target) {

    /**
     * Rejects null and blank components.
     *
     * @throws NullPointerException if a component is null
     * @throws IllegalArgumentException if a component is blank
     */
    public EntrypointConfig {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
    }
}
