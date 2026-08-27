package dev.hogwai.platform.runtime.invocation;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.StructuredPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerCompletionHandlerTest {

    @Test
    void dispatchesToRegisteredCallbacks() throws InterruptedException {
        WorkerCompletionHandler handler = new WorkerCompletionHandler();
        CountDownLatch latch = new CountDownLatch(1);
        ExecutionOutcome[] received = new ExecutionOutcome[1];

        handler.register("worker-1", outcome -> {
            received[0] = outcome;
            latch.countDown();
        });

        ExecutionOutcome outcome = ExecutionOutcome.materialized(new StructuredPayload(Map.of("result", "ok")));
        handler.dispatch("worker-1", outcome);

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received[0]).isNotNull();
    }

    @Test
    void doesNotDispatchToUnregisteredWorkers() throws InterruptedException {
        WorkerCompletionHandler handler = new WorkerCompletionHandler();
        CountDownLatch latch = new CountDownLatch(1);

        handler.register("worker-1", _ -> latch.countDown());

        ExecutionOutcome outcome = ExecutionOutcome.materialized(new StructuredPayload(Map.of("result", "ok")));
        handler.dispatch("worker-2", outcome);

        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void clearRemovesAllCallbacks() throws InterruptedException {
        WorkerCompletionHandler handler = new WorkerCompletionHandler();
        CountDownLatch latch = new CountDownLatch(1);

        handler.register("worker-1", _ -> latch.countDown());

        handler.clear("worker-1");

        ExecutionOutcome outcome = ExecutionOutcome.materialized(new StructuredPayload(Map.of("result", "ok")));
        handler.dispatch("worker-1", outcome);

        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isFalse();
    }
}
