package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.runtime.config.InputBindingConfig;
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
        return new ApplicationConfig("heron.dev/v1", "test", List.of(capabilities));
    }

    static CapabilityConfig capability(String id, String providerId, String version,
                                       Map<String, Object> config, List<InputBindingConfig> inputs) {
        return new CapabilityConfig(id, providerId, version, config, inputs);
    }

}
