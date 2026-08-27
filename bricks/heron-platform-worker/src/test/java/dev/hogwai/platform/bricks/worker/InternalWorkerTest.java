package dev.hogwai.platform.bricks.worker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.StructuredPayload;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalWorkerTest {

    private static final HostApplication NOOP_HOST = new HostApplication() {
        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return List.of(new EntrypointDescriptor("test", "/test"));
        }

        @Override
        public ExecutionOutcome execute(dev.hogwai.platform.spi.host.InvocationRequest request) {
            return ExecutionOutcome.materialized(new StructuredPayload(Map.of("result", "ok")));
        }

        @Override
        public void close() {
            // no-op
        }
    };

    @Test
    void exposesIdAndHost() {
        InternalWorker worker = new InternalWorker("my-worker", NOOP_HOST);
        assertThat(worker.id()).isEqualTo("my-worker");
        assertThat(worker.host()).isSameAs(NOOP_HOST);
    }

    @Test
    void delegatesEndpointsToHost() {
        InternalWorker worker = new InternalWorker("my-worker", NOOP_HOST);
        assertThat(worker.endpoints()).hasSize(1);
        assertThat(worker.endpoints().getFirst().id()).isEqualTo("test");
    }

    @Test
    void invokeDelegatesToHostAndReturnsResult() throws InterruptedException {
        InternalWorker worker = new InternalWorker("my-worker", NOOP_HOST);
        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "test", Map.of(), Duration.ofSeconds(5), Map.of()
        );

        CountDownLatch latch = new CountDownLatch(1);
        ExecutionOutcome[] received = new ExecutionOutcome[1];

        worker.invoke(request, outcome -> {
            received[0] = outcome;
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received[0]).isNotNull();
        assertThat(received[0].materialized()).isPresent();
    }
}
