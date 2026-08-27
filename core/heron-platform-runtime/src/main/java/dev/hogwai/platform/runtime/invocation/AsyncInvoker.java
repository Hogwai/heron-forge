package dev.hogwai.platform.runtime.invocation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;

/**
 * Orchestrator for asynchronous worker invocations.
 */
public class AsyncInvoker {

    /**
     * Invokes a worker asynchronously
     *
     * @param worker  the target worker
     * @param request the invocation request
     * @return a CompletableFuture with the result
     * @throws RuntimeException if the invocation fails or times out
     */
    public CompletableFuture<ExecutionOutcome> invoke(
            AsyncWorker worker,
            WorkerInvocationRequest request) {

        return CompletableFuture.supplyAsync(() -> {
            CompletableFuture<ExecutionOutcome> result = new CompletableFuture<>();
            worker.invoke(request, new WorkerCompletionCallback() {
                @Override
                public void onComplete(ExecutionOutcome outcome) {
                    result.complete(outcome);
                }

                @Override
                public void onFailure(Throwable error) {
                    result.completeExceptionally(error);
                }
            });
            try {
                return result.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Invocation interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Invocation failed", e.getCause());
            } catch (TimeoutException e) {
                throw new RuntimeException("Invocation timed out", e);
            }
        });
    }

    /**
     * Fan-out: invokes multiple workers in parallel on Virtual Threads.
     *
     * @param invocations pairs of (worker, request)
     * @return a CompletableFuture that completes when all invocations complete
     */
    public CompletableFuture<Void> fanOut(List<WorkerInvocation> invocations) {
        CompletableFuture<?>[] futures = invocations.stream()
                .map(inv -> invoke(inv.worker(), inv.request()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * A worker invocation pair.
     *
     * @param worker  the target worker
     * @param request the invocation request
     */
    public record WorkerInvocation(AsyncWorker worker, WorkerInvocationRequest request) {
    }
}
