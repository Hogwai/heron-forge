package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.WorkerConfig;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.snapshot.SnapshotBuilder;
import dev.hogwai.platform.runtime.snapshot.SnapshotCandidate;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerRegistry;
import dev.hogwai.platform.spi.provider.BuildContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotBuilderWorkerIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildPopulatesWorkerRegistryFromApplicationConfig() {
        List<BuildContext> captured = new ArrayList<>();
        SnapshotBuilderTestProviderFactory orders = new SnapshotBuilderTestProviderFactory(
                SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                ctx -> {
                    captured.add(ctx);
                    return new SnapshotBuilderTestInstance("orders", new ArrayList<>(), false);
                });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        WorkerConfig worker = new WorkerConfig("order-service", "http",
                Map.of("baseUrl", "https://orders.example.com"));
        ApplicationConfig app = new ApplicationConfig("heron.dev/v1", "test",
                List.of(new dev.hogwai.platform.runtime.config.CapabilityConfig("orders", "orders", "1.0.0",
                        Map.of("host", "localhost"), List.of())),
                List.of(), List.of(), List.of(worker));

        try (SnapshotCandidate candidate = builder.build(app)) {
            assertThat(candidate.snapshot().generationId()).isEqualTo("gen-1");
        }
        assertThat(captured).hasSize(1);
        WorkerRegistry workerRegistry = captured.getFirst().workerRegistry();
        Optional<AsyncWorker> actual = workerRegistry.find("order-service");
        assertThat(actual).isPresent();
        assertThat(actual.get().id())
                .isEqualTo("order-service");
        assertThat(workerRegistry.all()).hasSize(1);
    }

    @Test
    void buildWithNoWorkersLeavesRegistryEmpty() {
        List<BuildContext> captured = new ArrayList<>();
        SnapshotBuilderTestProviderFactory orders = new SnapshotBuilderTestProviderFactory(
                SnapshotBuilderTestSupport.source("orders", "1.0.0"),
                ctx -> {
                    captured.add(ctx);
                    return new SnapshotBuilderTestInstance("orders", new ArrayList<>(), false);
                });
        ProviderRegistry registry = new SnapshotBuilderTestRegistry(
                new ProviderRegistry.Registration(orders, orders.descriptor()));
        SnapshotBuilder builder = SnapshotBuilderTestSupport.builder(registry, "gen-1", CLOCK);

        ApplicationConfig app = SnapshotBuilderTestSupport.application("test",
                new SnapshotBuilderTestYaml.TestCap("orders", "orders", "1.0.0"));

        try (SnapshotCandidate c = builder.build(app)) {
            assertThat(c.snapshot().generationId()).isEqualTo("gen-1");
        }
        assertThat(captured.getFirst().workerRegistry().all()).isEmpty();
    }
}
