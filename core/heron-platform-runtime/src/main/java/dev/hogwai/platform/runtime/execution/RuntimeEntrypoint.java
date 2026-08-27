package dev.hogwai.platform.runtime.execution;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;

import java.util.Objects;

/**
 * Runtime-only association between a public entrypoint and its graph target.
 */
public record RuntimeEntrypoint(EntrypointDescriptor descriptor, String target) {

    public RuntimeEntrypoint {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
    }
}