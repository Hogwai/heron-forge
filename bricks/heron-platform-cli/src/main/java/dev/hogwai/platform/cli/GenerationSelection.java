package dev.hogwai.platform.cli;

import java.util.List;
import java.util.Optional;

import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;

/**
 * Selection policy for starting an application from the generation store.
 *
 * <p>The policy is deliberately extracted from the picocli commands so it can
 * be exercised unit-wise without touching a store or a host:
 * <ul>
 *   <li>an explicit generation id selects that generation whatever its status,
 *   except {@link GenerationStatus#RETIRED} which is always refused;</li>
 *   <li>a {@link GenerationStatus#DEPRECATED} generation selected explicitly is
 *   allowed but carries a warning;</li>
 *   <li>without an explicit id, the most recent {@link GenerationStatus#STABLE}
 *   generation is selected.</li>
 * </ul>
 *
 * <p>The resolver for rollbacks lives here too: the previous STABLE generation
 * is the second most recent STABLE entry of the history. The platform being an
 * activation-at-boot platform (no hot-swap yet), rollback resolves and prints
 * the target; it never restarts anything itself.
 */
final class GenerationSelection {

    private GenerationSelection() {
        // no instances
    }

    /**
     * Selects the generation to start from the given history.
     *
     * @param history               the application history, most recent first
     * @param requestedGenerationId the explicitly requested generation id, or
     *                              {@code null}/blank for the default policy
     * @return the selected generation and an optional user-facing warning
     * @throws IllegalArgumentException when the request cannot be satisfied
     *                                  (unknown generation, RETIRED generation,
     *                                  or no STABLE generation available)
     */
    static Selected select(List<GenerationRecord> history, String requestedGenerationId) {
        if (requestedGenerationId != null && !requestedGenerationId.isBlank()) {
            return explicit(history, requestedGenerationId);
        }
        return latestStable(history);
    }

    /**
     * Resolves the rollback target: the most recent STABLE generation strictly
     * older than the current latest STABLE one.
     *
     * @param history the application history, most recent first
     * @return the previous STABLE generation, or empty when fewer than two
     * STABLE generations exist
     */
    static Optional<GenerationRecord> previousStable(List<GenerationRecord> history) {
        List<GenerationRecord> stables = stables(history);
        if (stables.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(stables.get(1));
    }

    private static Selected explicit(List<GenerationRecord> history, String requestedGenerationId) {
        GenerationRecord generationRecord = history.stream()
                .filter(candidate -> candidate.generationId().equals(requestedGenerationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("generation '%s' not found"
                        .formatted(requestedGenerationId)));
        if (generationRecord.status() == GenerationStatus.RETIRED) {
            throw new IllegalArgumentException(
                    "generation '%s' is RETIRED and cannot be started".formatted(requestedGenerationId));
        }
        if (generationRecord.status() == GenerationStatus.DEPRECATED) {
            return new Selected(generationRecord,
                    "generation '%s' is DEPRECATED; plan a roll-forward".formatted(requestedGenerationId));
        }
        return new Selected(generationRecord, null);
    }

    private static Selected latestStable(List<GenerationRecord> history) {
        return stables(history).stream()
                .findFirst()
                .map(generationRecord -> new Selected(generationRecord, null))
                .orElseThrow(() -> new IllegalArgumentException(
                        "no STABLE generation found; pass --generation explicitly"));
    }

    private static List<GenerationRecord> stables(List<GenerationRecord> history) {
        return history.stream()
                .filter(candidate -> candidate.status() == GenerationStatus.STABLE)
                .toList();
    }

    /**
     * Result of a selection: the chosen generationRecord plus an optional warning line
     * to surface before activation.
     */
    record Selected(GenerationRecord generationRecord, String warning) {
    }
}
