package dev.hogwai.platform.host.api;

import java.util.Objects;

/** Describes an application entrypoint exposed by a host.
 *
 * @param id   stable entrypoint identifier
 * @param path absolute transport path
 */
public record EntrypointDescriptor(String id, String path) {

    /** Validates the entrypoint identifier and path. */
    public EntrypointDescriptor {
        requireNonBlank(id, "id");
        requireNonBlank(path, "path");
        if (!path.startsWith("/") || path.indexOf('?') >= 0 || path.indexOf('#') >= 0) {
            throw new IllegalArgumentException("path must be absolute and must not contain a query or fragment");
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
