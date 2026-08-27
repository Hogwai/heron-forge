package dev.hogwai.platform.cli;

import java.time.Instant;
import java.util.List;

import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the generation selection policy used by {@code heron start}.
 */
class GenerationSelectionTest {

    private static final Instant BASE = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void defaultSelectionPicksTheMostRecentStableGeneration() {
        GenerationRecord newestStable = generateRecord("stable-2", GenerationStatus.STABLE, 3);
        List<GenerationRecord> history = List.of(
                generateRecord("experimental", GenerationStatus.EXPERIMENTAL, 4),
                newestStable,
                generateRecord("deprecated", GenerationStatus.DEPRECATED, 2),
                generateRecord("stable-1", GenerationStatus.STABLE, 1));

        assertThat(GenerationSelection.select(history, null).generationRecord()).isSameAs(newestStable);
        assertThat(GenerationSelection.select(history, " ").generationRecord()).isSameAs(newestStable);
    }

    @Test
    void explicitExperimentalGenerationIsAllowedWithoutWarning() {
        GenerationRecord experimental = generateRecord("exp", GenerationStatus.EXPERIMENTAL, 1);

        GenerationSelection.Selected selected =
                GenerationSelection.select(List.of(experimental), "exp");

        assertThat(selected.generationRecord()).isSameAs(experimental);
        assertThat(selected.warning()).isNull();
    }

    @Test
    void explicitDeprecatedGenerationIsAllowedWithAWarning() {
        GenerationRecord deprecated = generateRecord("old", GenerationStatus.DEPRECATED, 1);

        GenerationSelection.Selected selected = GenerationSelection.select(List.of(deprecated), "old");

        assertThat(selected.generationRecord()).isSameAs(deprecated);
        assertThat(selected.warning()).contains("DEPRECATED").contains("old");
    }

    @Test
    void retiredGenerationsAreAlwaysRefused() {
        GenerationRecord retired = generateRecord("gone", GenerationStatus.RETIRED, 1);
        var retiredList = List.of(retired);
        assertThatThrownBy(() -> GenerationSelection.select(retiredList, "gone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RETIRED");
    }

    @Test
    void unknownExplicitGenerationIsRefused() {
        GenerationRecord stable = generateRecord("stable", GenerationStatus.STABLE, 1);
        var stableList = List.of(stable);
        assertThatThrownBy(() -> GenerationSelection.select(stableList, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void defaultSelectionWithoutAnyStableGenerationIsRefused() {
        List<GenerationRecord> history = List.of(
                generateRecord("deprecated", GenerationStatus.DEPRECATED, 2),
                generateRecord("retired", GenerationStatus.RETIRED, 1));

        assertThatThrownBy(() -> GenerationSelection.select(history, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STABLE");
    }

    @Test
    void rollbackResolvesTheSecondMostRecentStableGeneration() {
        GenerationRecord previous = generateRecord("stable-1", GenerationStatus.STABLE, 1);
        List<GenerationRecord> history = List.of(
                generateRecord("stable-2", GenerationStatus.STABLE, 3),
                generateRecord("experimental", GenerationStatus.EXPERIMENTAL, 2),
                previous);

        assertThat(GenerationSelection.previousStable(history)).contains(previous);
    }

    @Test
    void rollbackRequiresAtLeastTwoStableGenerations() {
        assertThat(GenerationSelection.previousStable(
                List.of(generateRecord("only", GenerationStatus.STABLE, 1)))).isEmpty();
        assertThat(GenerationSelection.previousStable(List.of())).isEmpty();
    }

    private static GenerationRecord generateRecord(String generationId, GenerationStatus status, int offsetSeconds) {
        String rawYaml = RegistryCliTestSupport.yaml(generationId);
        return new GenerationRecord(RegistryCliTestSupport.APPLICATION, generationId,
                RegistryCliTestSupport.sha256(rawYaml), rawYaml, status,
                BASE.plusSeconds(offsetSeconds), "tester");
    }
}
