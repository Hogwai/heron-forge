package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapabilityInputsTest {

    private static final PortId A = new PortId("a");
    private static final PortId B = new PortId("b");

    private static MaterializedDataSet dataSet(String id) {
        return new MaterializedDataSet(ProviderTestSupport.schema(id), List.of(),
                new dev.hogwai.platform.spi.data.DataSetMetadata("ds",
                        new dev.hogwai.platform.spi.data.DataSetLimits(10, 1000)), 0);
    }

    @Test
    void acceptsValidMap() {
        CapabilityInputs inputs = CapabilityInputs.of(Map.of(A, dataSet("a"), B, dataSet("b")));
        assertThat(inputs.size()).isEqualTo(2);
        assertThat(inputs.contains(A)).isTrue();
        assertThat(inputs.get(A).schema().identifier()).isEqualTo("a");
        assertThat(inputs.portIds()).containsExactlyInAnyOrder(A, B);
    }

    @Test
    void acceptsValidList() {
        CapabilityInputs inputs = CapabilityInputs.of(List.of(
                new CapabilityInput(A, dataSet("a")), new CapabilityInput(B, dataSet("b"))));
        assertThat(inputs.size()).isEqualTo(2);
        assertThat(inputs.get(B).schema().identifier()).isEqualTo("b");
    }

    @Test
    void emptyInputsAreEmpty() {
        CapabilityInputs inputs = CapabilityInputs.of(Map.of());
        assertThat(inputs.isEmpty()).isTrue();
        assertThat(inputs.size()).isZero();
    }

    @Test
    void rejectsNullMapAndList() {
        assertThatThrownBy(() -> CapabilityInputs.of((Map<PortId, MaterializedDataSet>) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CapabilityInputs.of((List<CapabilityInput>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullKeyAndValue() {
        Map<PortId, MaterializedDataSet> nullKey = new HashMap<>();
        nullKey.put(A, dataSet("a"));
        nullKey.put(null, dataSet("b"));
        assertThatThrownBy(() -> CapabilityInputs.of(nullKey)).isInstanceOf(NullPointerException.class);

        Map<PortId, MaterializedDataSet> nullValue = new HashMap<>();
        nullValue.put(A, null);
        assertThatThrownBy(() -> CapabilityInputs.of(nullValue)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsDuplicatePortsInList() {
        List<CapabilityInput> duplicates = List.of(
                new CapabilityInput(A, dataSet("a")), new CapabilityInput(A, dataSet("a2")));
        assertThatThrownBy(() -> CapabilityInputs.of(duplicates))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullElementInList() {
        List<CapabilityInput> withNull = new ArrayList<>();
        withNull.add(new CapabilityInput(A, dataSet("a")));
        withNull.add(null);
        assertThatThrownBy(() -> CapabilityInputs.of(withNull)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullPortIdInCapabilityInput() {
        MaterializedDataSet ds = dataSet("a");
        assertThatThrownBy(() -> new CapabilityInput(null, ds)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CapabilityInput(A, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void viewsAreImmutable() {
        CapabilityInputs inputs = CapabilityInputs.of(Map.of(A, dataSet("a")));
        MaterializedDataSet ds = dataSet("b");
        Map<PortId, MaterializedDataSet> mapView = inputs.asMap();
        Set<PortId> portIdsView = inputs.portIds();
        assertThatThrownBy(() -> mapView.put(B, ds))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> portIdsView.add(B)).isInstanceOf(UnsupportedOperationException.class);
    }
}
