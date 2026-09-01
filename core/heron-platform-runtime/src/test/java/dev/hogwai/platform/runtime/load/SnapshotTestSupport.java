package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Package-private helpers for the lifecycle package tests.
 */
final class SnapshotTestSupport {

    private SnapshotTestSupport() {
        // no instances
    }

    static CapabilityGraph graph() {
        ApplicationConfig app = new ApplicationConfig("heron.dev/v1", "test",
                List.of(new CapabilityConfig("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of())));
        return new GraphCompiler().compile(app, new ProviderResolver(new ServiceLoaderProviderRegistry()));
    }

    static CapabilityInstance instance() {
        return new CapabilityInstance() {
            private final int id = INSTANCE_COUNTER.getAndIncrement();

            @Override
            public DataSet execute(CapabilityInputs inputs, ExecutionContext context) {
                throw new UnsupportedOperationException("not used in snapshot tests");
            }

            @Override
            public String toString() {
                return "Instance#" + id;
            }
        };
    }

    static Supplier<CapabilityInstance> factory() {
        return SnapshotTestSupport::instance;
    }

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
}
