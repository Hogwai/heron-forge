package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.load.config.ApplicationConfig;
import dev.hogwai.platform.runtime.load.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import java.util.List;
import java.util.Map;

/**
 * Package-private helpers for the lifecycle package tests.
 */
final class SnapshotTestSupport {

    private SnapshotTestSupport() {
        // no instances
    }

    static CapabilityGraph graph() {
        ApplicationConfig app = new ApplicationConfig("platform.dev/v1alpha1", "Application", "test",
                List.of(new CapabilityConfig("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of())));
        return new GraphCompiler().compile(app, new ProviderResolver(new ServiceLoaderProviderRegistry()));
    }

    static RuntimeSnapshot snapshot(String generationId) {
        return new RuntimeSnapshot(generationId, graph(), Map.of("orders", instance()));
    }

    static CapabilityInstance instance() {
        return new CapabilityInstance() {
            @Override
            public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
                throw new UnsupportedOperationException("not used in snapshot tests");
            }
        };
    }
}
