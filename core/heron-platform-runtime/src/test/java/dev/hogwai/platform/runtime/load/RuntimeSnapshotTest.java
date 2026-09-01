package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.runtime.execution.RuntimeEntrypoint;
import dev.hogwai.platform.runtime.snapshot.RuntimeSnapshot;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RuntimeSnapshot} immutability and validation.
 */
class RuntimeSnapshotTest {

    @Test
    void exposesGenerationIdAndGraph() {
        CapabilityGraph graph = SnapshotTestSupport.graph();
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", graph, Map.of("orders", SnapshotTestSupport.factory()));

        assertThat(snapshot.generationId()).isEqualTo("gen-1");
        assertThat(snapshot.graph()).isSameAs(graph);
    }

    @Test
    void rejectsBlankGenerationId() {
        assertThatThrownBy(() -> new RuntimeSnapshot("  ", SnapshotTestSupport.graph(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullGenerationId() {
        assertThatThrownBy(() -> new RuntimeSnapshot(null, SnapshotTestSupport.graph(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullGraph() {
        assertThatThrownBy(() -> new RuntimeSnapshot("gen-1", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullInstances() {
        assertThatThrownBy(() -> new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankInstanceKey() {
        assertThatThrownBy(() -> new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(),
                Map.of("", SnapshotTestSupport.factory())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingInstance() {
        assertThatThrownBy(() -> new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExtraInstance() {
        assertThatThrownBy(() -> new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(),
                Map.of("orders", SnapshotTestSupport.factory(), "extra", SnapshotTestSupport.factory())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesFactories() {
        Supplier<CapabilityInstance> a = SnapshotTestSupport.factory();
        Supplier<CapabilityInstance> b = SnapshotTestSupport.factory();
        Map<String, Supplier<CapabilityInstance>> mutable = new LinkedHashMap<>();
        mutable.put("orders", a);
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(), mutable);

        mutable.put("extra", b);

        assertThat(snapshot.instanceFactories()).containsOnlyKeys("orders");
        assertThat(snapshot.instance("orders")).isPresent();
        assertThat(snapshot.instance("extra")).isEmpty();
    }

    @Test
    void exposesImmutableFactories() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(),
                Map.of("orders", SnapshotTestSupport.factory()));

        assertThatThrownBy(() -> snapshot.instanceFactories().put("x", SnapshotTestSupport.factory()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void instanceReturnsOptionalAndCreatesFreshInstance() {
        Supplier<CapabilityInstance> factory = SnapshotTestSupport.factory();
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(), Map.of("orders", factory));

        CapabilityInstance first = snapshot.instance("orders").orElseThrow();
        CapabilityInstance second = snapshot.instance("orders").orElseThrow();

        assertThat(first).isNotSameAs(second);
        assertThat(snapshot.instance("missing")).isEmpty();
    }

    @Test
    void runtimeEntrypointValidatesItsPrivateTargetMapping() {
        EntrypointDescriptor descriptor = new EntrypointDescriptor("read", "/read");
        RuntimeEntrypoint entrypoint = new RuntimeEntrypoint(descriptor, "orders");

        assertThat(entrypoint.descriptor()).isSameAs(descriptor);
        assertThat(entrypoint.target()).isEqualTo("orders");
        assertThatThrownBy(() -> new RuntimeEntrypoint(null, "orders"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RuntimeEntrypoint(descriptor, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
