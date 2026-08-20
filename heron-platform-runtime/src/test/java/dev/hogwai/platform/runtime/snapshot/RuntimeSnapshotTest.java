package dev.hogwai.platform.runtime.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.runtime.graph.CapabilityGraph;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RuntimeSnapshot} immutability and validation.
 */
class RuntimeSnapshotTest {

    @Test
    void exposesGenerationIdAndGraph() {
        CapabilityGraph graph = SnapshotTestSupport.graph();
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", graph, Map.of("orders", SnapshotTestSupport.instance()));

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
                Map.of("", SnapshotTestSupport.instance())))
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
                Map.of("orders", SnapshotTestSupport.instance(), "extra", SnapshotTestSupport.instance())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesInstances() {
        CapabilityInstance a = SnapshotTestSupport.instance();
        CapabilityInstance b = SnapshotTestSupport.instance();
        Map<String, CapabilityInstance> mutable = new LinkedHashMap<>();
        mutable.put("orders", a);
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(), mutable);

        mutable.put("extra", b);

        assertThat(snapshot.instances()).containsOnlyKeys("orders");
        assertThat(snapshot.instance("orders")).get().isSameAs(a);
        assertThat(snapshot.instance("extra")).isEmpty();
    }

    @Test
    void exposesImmutableInstances() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(),
                Map.of("orders", SnapshotTestSupport.instance()));

        assertThatThrownBy(() -> snapshot.instances().put("x", SnapshotTestSupport.instance()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void instanceReturnsOptional() {
        CapabilityInstance a = SnapshotTestSupport.instance();
        RuntimeSnapshot snapshot = new RuntimeSnapshot("gen-1", SnapshotTestSupport.graph(), Map.of("orders", a));

        assertThat(snapshot.instance("orders")).get().isSameAs(a);
        assertThat(snapshot.instance("missing")).isEmpty();
    }
}