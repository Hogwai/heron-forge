package dev.hogwai.platform.registry;

import java.time.Instant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic concurrency check: parallel threads save distinct generations
 * of the same application through one shared {@link FileGenerationStore} and
 * no write is lost (start latch releases all writers at once).
 */
class FileGenerationStoreConcurrencyTest {

    private static final int THREADS = 8;
    private static final String APPLICATION = "supply-chain-demo";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void savesDistinctGenerationsConcurrentlyWithoutLoss() throws Exception {
        FileGenerationStore store = new FileGenerationStore(tempDir);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String generationId = "gen-%02d".formatted(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    store.save(new GenerationRecord(APPLICATION, generationId,
                            "sha256-" + generationId, "kind: test\nname: " + generationId + "\n",
                            GenerationStatus.EXPERIMENTAL, T0, "thread"));
                    return generationId;
                }));
            }
            start.countDown();
            List<String> saved = new ArrayList<>();
            for (Future<String> future : futures) {
                saved.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(saved).hasSize(THREADS);
            assertThat(store.history(APPLICATION))
                    .extracting(GenerationRecord::generationId)
                    .containsExactlyInAnyOrderElementsOf(saved);
            for (String generationId : saved) {
                assertThat(store.find(APPLICATION, generationId))
                        .hasValueSatisfying(found -> {
                            assertThat(found.generationId()).isEqualTo(generationId);
                            assertThat(found.rawYaml()).contains(generationId);
                        });
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
