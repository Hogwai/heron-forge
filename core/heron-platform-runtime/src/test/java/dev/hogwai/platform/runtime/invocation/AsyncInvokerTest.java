package dev.hogwai.platform.runtime.invocation;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.StructuredPayload;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncInvokerTest {

    private static final HostApplication NOOP_HOST = new HostApplication() {
        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return List.of();
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

    private static AsyncWorker successfulWorker() {
        return new AsyncWorker() {
            @Override
            public String id() {
                return "test-worker";
            }

            @Override
            public HostApplication host() {
                return NOOP_HOST;
            }

            @Override
            public List<EntrypointDescriptor> endpoints() {
                return List.of();
            }

            @Override
            public void invoke(WorkerInvocationRequest request, WorkerCompletionCallback callback) {
                callback.onComplete(ExecutionOutcome.materialized(new StructuredPayload(Map.of("result", "ok"))));
            }
        };
    }

    private static AsyncWorker failingWorker() {
        return new AsyncWorker() {
            @Override
            public String id() {
                return "failing-worker";
            }

            @Override
            public HostApplication host() {
                return NOOP_HOST;
            }

            @Override
            public List<EntrypointDescriptor> endpoints() {
                return List.of();
            }

            @Override
            public void invoke(WorkerInvocationRequest request, WorkerCompletionCallback callback) {
                callback.onFailure(new RuntimeException("test error"));
            }
        };
    }

    @Test
    void invokeReturnsSuccessfulResult() throws Exception {
        AsyncInvoker invoker = new AsyncInvoker();
        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "test", Map.of(), Duration.ofSeconds(5), Map.of()
        );

        CompletableFuture<ExecutionOutcome> future = invoker.invoke(successfulWorker(), request);
        ExecutionOutcome outcome = future.get();

        assertThat(outcome).isNotNull();
        assertThat(outcome.materialized()).isPresent();
    }

    @Test
    void invokeThrowsOnFailure() {
        AsyncInvoker invoker = new AsyncInvoker();
        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "test", Map.of(), Duration.ofSeconds(5), Map.of()
        );

        CompletableFuture<ExecutionOutcome> future = invoker.invoke(failingWorker(), request);

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void fanOutCompletesAllInvocations() throws Exception {
        AsyncInvoker invoker = new AsyncInvoker();
        WorkerInvocationRequest request = new WorkerInvocationRequest(
                "test", Map.of(), Duration.ofSeconds(5), Map.of()
        );

        List<AsyncInvoker.WorkerInvocation> invocations = List.of(
                new AsyncInvoker.WorkerInvocation(successfulWorker(), request),
                new AsyncInvoker.WorkerInvocation(successfulWorker(), request),
                new AsyncInvoker.WorkerInvocation(successfulWorker(), request)
        );

        CompletableFuture<Void> fanOut = invoker.fanOut(invocations);
        fanOut.get();

        assertThat(fanOut.isDone()).isTrue();
    }
}
