package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.host.api.EntrypointDescriptor;
import java.util.Objects;

/** Runtime-only association between a public entrypoint and its graph target. */
final class RuntimeEntrypoint {

    private final EntrypointDescriptor descriptor;
    private final String target;

    RuntimeEntrypoint(EntrypointDescriptor descriptor, String target) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
        if (this.target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
    }

    EntrypointDescriptor descriptor() {
        return descriptor;
    }

    String target() {
        return target;
    }
}
