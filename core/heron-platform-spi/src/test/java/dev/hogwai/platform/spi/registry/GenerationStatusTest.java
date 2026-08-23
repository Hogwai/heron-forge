package dev.hogwai.platform.spi.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationStatusTest {

    @Test
    void exposesExactlyTheFourLifecycleValues() {
        assertThat(GenerationStatus.values()).containsExactly(
                GenerationStatus.EXPERIMENTAL,
                GenerationStatus.STABLE,
                GenerationStatus.DEPRECATED,
                GenerationStatus.RETIRED);
    }

    @Test
    void declaresValuesInMonotoneLifecycleOrder() {
        List<GenerationStatus> values = List.of(GenerationStatus.values());
        for (int i = 1; i < values.size(); i++) {
            assertThat(values.indexOf(values.get(i))).isGreaterThan(values.indexOf(values.get(i - 1)));
        }
        assertThat(values.getLast()).isEqualTo(GenerationStatus.RETIRED);
    }
}
