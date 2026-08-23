package dev.hogwai.platform.spi.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link GenerationStore} contract through an in-memory
 * implementation defined here, not in main sources.
 */
class GenerationStoreContractTest {

    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2030-01-02T00:00:00Z");
    private static final Instant T2 = Instant.parse("2030-01-03T00:00:00Z");

    private static GenerationRecord generateRecord(String generationId, Instant createdAt, GenerationStatus status) {
        return new GenerationRecord(
                "app",
                generationId,
                "sha-%s".formatted(generationId),
                "raw: %s".formatted(generationId),
                status,
                createdAt,
                "cli");
    }

    private static final List<GenerationStatus> LIFECYCLE_ORDER =
            List.of(GenerationStatus.EXPERIMENTAL, GenerationStatus.STABLE,
                    GenerationStatus.DEPRECATED, GenerationStatus.RETIRED);

    /**
     * Minimal in-memory implementation of the contract, used to exercise the
     * documented semantics (idempotent save, deterministic history ordering,
     * monotone transitions, idempotent close).
     */
    private static final class InMemoryGenerationStore implements GenerationStore {
        private final Map<String, GenerationRecord> records = new HashMap<>();
        private boolean closed;

        private static String key(String applicationId, String generationId) {
            return applicationId + "\u0000" + generationId;
        }

        @Override
        public void save(GenerationRecord generationRecord) {
            if (closed) {
                throw new IllegalStateException("store is closed");
            }
            records.put(key(generationRecord.applicationId(), generationRecord.generationId()), generationRecord);
        }

        @Override
        public Optional<GenerationRecord> find(String applicationId, String generationId) {
            return Optional.ofNullable(records.get(key(applicationId, generationId)));
        }

        @Override
        public List<GenerationRecord> history(String applicationId) {
            return records.values().stream()
                    .filter(generationRecord -> generationRecord.applicationId().equals(applicationId))
                    .sorted((left, right) -> {
                        int byDate = right.createdAt().compareTo(left.createdAt());
                        return byDate != 0 ? byDate : left.generationId().compareTo(right.generationId());
                    })
                    .toList();
        }

        @Override
        public boolean transition(String applicationId, String generationId, GenerationStatus target) {
            GenerationRecord current = find(applicationId, generationId).orElse(null);
            if (current == null
                    || LIFECYCLE_ORDER.indexOf(target) <= LIFECYCLE_ORDER.indexOf(current.status())) {
                return false;
            }
            save(new GenerationRecord(current.applicationId(), current.generationId(),
                    current.configSha256(), current.rawYaml(), target, current.createdAt(),
                    current.createdBy()));
            return true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void savesAndFindsRecords() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            assertThat(store.find("app", "gen-1")).isEmpty();

            store.save(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));
            assertThat(store.find("app", "gen-1"))
                    .contains(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));

            assertThat(store.find("other", "gen-1")).isEmpty();
            assertThat(store.find("app", "gen-2")).isEmpty();
        }
    }

    @Test
    void saveIsIdempotentPerApplicationAndGenerationPair() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            store.save(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));
            store.save(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));

            assertThat(store.history("app")).hasSize(1);

            store.save(generateRecord("gen-1", T0, GenerationStatus.STABLE));
            assertThat(store.find("app", "gen-1"))
                    .contains(generateRecord("gen-1", T0, GenerationStatus.STABLE));
            assertThat(store.history("app")).hasSize(1);
        }
    }

    @Test
    void historySortsMostRecentFirstWithDeterministicTieBreak() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            store.save(generateRecord("b", T1, GenerationStatus.EXPERIMENTAL));
            store.save(generateRecord("c", T2, GenerationStatus.EXPERIMENTAL));
            store.save(generateRecord("a", T1, GenerationStatus.EXPERIMENTAL));

            assertThat(store.history("app"))
                    .extracting(GenerationRecord::generationId)
                    .containsExactly("c", "a", "b");

            assertThat(store.history("unknown")).isEmpty();
        }
    }

    @Test
    void historyReturnsImmutableListOrDefensiveCopy() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            store.save(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));

            List<GenerationRecord> history = store.history("app");
            try {
                history.clear();
            } catch (UnsupportedOperationException _) {
                // an immutable view is a valid contract fulfilment
            }
            assertThat(store.history("app")).hasSize(1);
        }
    }

    @Test
    void transitionFollowsTheFullMonotonePath() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            store.save(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));

            assertThat(store.transition("app", "gen-1", GenerationStatus.STABLE)).isTrue();
            assertThat(store.find("app", "gen-1"))
                    .contains(generateRecord("gen-1", T0, GenerationStatus.STABLE));
            assertThat(store.transition("app", "gen-1", GenerationStatus.DEPRECATED)).isTrue();
            assertThat(store.transition("app", "gen-1", GenerationStatus.RETIRED)).isTrue();
            assertThat(store.find("app", "gen-1"))
                    .contains(generateRecord("gen-1", T0, GenerationStatus.RETIRED));
        }
    }

    @Test
    void transitionKeepsEveryOtherComponentUnchanged() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            GenerationRecord original = generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL);
            store.save(original);

            assertThat(store.transition("app", "gen-1", GenerationStatus.STABLE)).isTrue();

            assertThat(store.find("app", "gen-1")).contains(
                    new GenerationRecord(original.applicationId(), original.generationId(),
                            original.configSha256(), original.rawYaml(), GenerationStatus.STABLE,
                            original.createdAt(), original.createdBy()));
        }
    }

    @Test
    void transitionRejectsBackwardMovesAndSameStatus() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            store.save(generateRecord("gen-1", T0, GenerationStatus.DEPRECATED));

            assertThat(store.transition("app", "gen-1", GenerationStatus.EXPERIMENTAL)).isFalse();
            assertThat(store.transition("app", "gen-1", GenerationStatus.STABLE)).isFalse();
            assertThat(store.transition("app", "gen-1", GenerationStatus.DEPRECATED)).isFalse();
            assertThat(store.find("app", "gen-1"))
                    .contains(generateRecord("gen-1", T0, GenerationStatus.DEPRECATED));
        }
    }

    @Test
    void retiredIsTerminal() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            store.save(generateRecord("gen-1", T0, GenerationStatus.RETIRED));

            for (GenerationStatus status : GenerationStatus.values()) {
                assertThat(store.transition("app", "gen-1", status)).isFalse();
            }
            assertThat(store.find("app", "gen-1"))
                    .contains(generateRecord("gen-1", T0, GenerationStatus.RETIRED));
        }
    }

    @Test
    void transitionReturnsFalseForUnknownRecords() {
        try (InMemoryGenerationStore store = new InMemoryGenerationStore()) {
            assertThat(store.transition("app", "missing", GenerationStatus.STABLE)).isFalse();
            store.save(generateRecord("gen-1", T0, GenerationStatus.EXPERIMENTAL));
            assertThat(store.transition("other", "gen-1", GenerationStatus.STABLE)).isFalse();
        }
    }

    @Test
    void closeIsIdempotent() {
        InMemoryGenerationStore store = new InMemoryGenerationStore();
        store.close();
        store.close();
        assertThat(store.closed).isTrue();
    }
}
