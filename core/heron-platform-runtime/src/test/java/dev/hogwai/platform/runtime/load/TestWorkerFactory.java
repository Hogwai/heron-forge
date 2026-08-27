package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.invocation.AsyncWorker;
import dev.hogwai.platform.spi.invocation.WorkerCompletionCallback;
import dev.hogwai.platform.spi.invocation.WorkerFactory;
import dev.hogwai.platform.spi.invocation.WorkerInvocationRequest;

import java.util.List;
import java.util.Map;

/**
 * Test worker factory for http transport.
 */
public final class TestWorkerFactory implements WorkerFactory {

    @Override
    public String transport() {
        return "http";
    }

    @Override
    public AsyncWorker create(String id, Map<String, Object> config) {
        return new AsyncWorker() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public HostApplication host() {
                return null;
            }

            @Override
            public List<EntrypointDescriptor> endpoints() {
                return List.of();
            }

            @Override
            public void invoke(WorkerInvocationRequest request, WorkerCompletionCallback callback) {
                // no-op for test
            }
        };
    }
}
