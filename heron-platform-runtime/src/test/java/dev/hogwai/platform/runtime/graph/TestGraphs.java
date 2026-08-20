package dev.hogwai.platform.runtime.graph;

import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.config.input.InputBindingConfig;
import dev.hogwai.platform.spi.CapabilityKind;
import java.util.List;
import java.util.Map;

/**
 * Package-private helpers for building application configurations in the
 * {@link GraphCompilerTest}.
 */
final class TestGraphs {

    private TestGraphs() {
        // no instances
    }

    static ApplicationConfig app(CapabilityConfig... capabilities) {
        return new ApplicationConfig("platform.dev/v1alpha1", "Application", "test", List.of(capabilities));
    }

    static CapabilityConfig capability(String id, CapabilityKind type, String providerId, String version,
                                       Map<String, Object> config, List<InputBindingConfig> inputs) {
        return new CapabilityConfig(id, type, providerId, version, config, inputs);
    }
}