package dev.hogwai.platform.runtime.registry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import dev.hogwai.platform.spi.registry.GenerationStore;

/**
 * In-memory {@link GenerationStore} fixture for the registry tests.
 */
final class InMemoryGenerationStore implements GenerationStore {

    private record Key(String applicationId, String generationId) {
    }

    private final Map<Key, GenerationRecord> records = new LinkedHashMap<>();

    @Override
    public void save(GenerationRecord generationRecord) {
        records.put(new Key(generationRecord.applicationId(), generationRecord.generationId()), generationRecord);
    }

    @Override
    public Optional<GenerationRecord> find(String applicationId, String generationId) {
        return Optional.ofNullable(records.get(new Key(applicationId, generationId)));
    }

    @Override
    public List<GenerationRecord> history(String applicationId) {
        return records.values().stream()
                .filter(generationRecord -> generationRecord.applicationId().equals(applicationId))
                .sorted(Comparator.comparing(GenerationRecord::createdAt).reversed()
                        .thenComparing(GenerationRecord::generationId))
                .toList();
    }

    @Override
    public boolean transition(String applicationId, String generationId, GenerationStatus target) {
        Key key = new Key(applicationId, generationId);
        GenerationRecord generationRecord = records.get(key);
        if (generationRecord == null || rank(target) <= rank(generationRecord.status())) {
            return false;
        }
        records.put(key, new GenerationRecord(generationRecord.applicationId(), generationRecord.generationId(), generationRecord.configSha256(),
                generationRecord.rawYaml(), target, generationRecord.createdAt(), generationRecord.createdBy()));
        return true;
    }

    @Override
    public void close() {
        // nothing to release
    }

    int size() {
        return records.size();
    }

    private static int rank(GenerationStatus status) {
        return switch (status) {
            case EXPERIMENTAL -> 0;
            case STABLE -> 1;
            case DEPRECATED -> 2;
            case RETIRED -> 3;
        };
    }
}
