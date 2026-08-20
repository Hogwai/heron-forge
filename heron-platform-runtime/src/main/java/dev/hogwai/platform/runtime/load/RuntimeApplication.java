package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.host.api.EntrypointDescriptor;
import dev.hogwai.platform.host.api.FailureCode;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.InvocationFailure;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationResult;
import dev.hogwai.platform.host.api.InvocationSuccess;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.execution.CancellationToken;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner of one loaded candidate and its provider resources. */
final class RuntimeApplication implements HostApplication {

    private final SnapshotCandidate candidate;
    private final List<RuntimeEntrypoint> runtimeEntrypoints;
    private final List<EntrypointDescriptor> entrypoints;
    private final PullInvoker invoker;
    private final AtomicBoolean closed = new AtomicBoolean();

    RuntimeApplication(SnapshotCandidate candidate, List<RuntimeEntrypoint> entrypoints, Clock clock) {
        this.candidate = Objects.requireNonNull(candidate, "candidate must not be null");
        this.runtimeEntrypoints = List.copyOf(Objects.requireNonNull(entrypoints, "entrypoints must not be null"));
        this.entrypoints = this.runtimeEntrypoints.stream()
                .map(RuntimeEntrypoint::descriptor)
                .toList();
        this.invoker = new PullInvoker(clock);
    }

    @Override
    public List<EntrypointDescriptor> entrypoints() {
        return entrypoints;
    }

    @Override
    public InvocationResult invoke(InvocationRequest request) {
        if (closed.get()) {
            return new InvocationFailure(FailureCode.INTERNAL, "application is closed");
        }
        if (request == null) {
            return new InvocationFailure(FailureCode.INVALID_REQUEST, "invalid request");
        }
        RuntimeEntrypoint entrypoint = runtimeEntrypoints.stream()
                .filter(candidate -> candidate.descriptor().id().equals(request.entrypointId()))
                .findFirst()
                .orElse(null);
        if (entrypoint == null) {
            return new InvocationFailure(FailureCode.ENTRYPOINT_NOT_FOUND, "entrypoint not found");
        }

        try {
            ExecutionContext context = new ExecutionContext(request.requestId(), candidate.snapshot().generationId(),
                    request.deadline(), cancellationToken(request), request.correlationId());
            MaterializedDataSet result = invoker.invokeTarget(candidate.snapshot(), entrypoint.target(), context);
            return new InvocationSuccess(StructuredPayloadProjector.project(result));
        } catch (PlatformException failure) {
            return failure(failure.code());
        } catch (RuntimeException failure) {
            return new InvocationFailure(FailureCode.INTERNAL, "internal invocation failure");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            candidate.close();
        }
    }

    private static InvocationFailure failure(PlatformErrorCode code) {
        return FailureMapper.map(code);
    }

    private static CancellationToken cancellationToken(InvocationRequest request) {
        return request.cancellationSignal()::isCancellationRequested;
    }

    private static final class FailureMapper {
        private FailureMapper() {
            // no instances
        }

        private static InvocationFailure map(PlatformErrorCode code) {
            return switch (code) {
                case DEADLINE_EXCEEDED -> new InvocationFailure(FailureCode.DEADLINE_EXCEEDED, "deadline exceeded");
                case CANCELLATION_REQUESTED -> new InvocationFailure(FailureCode.CANCELLATION_REQUESTED,
                        "cancellation requested");
                case CAPABILITY_EXECUTION_ERROR -> new InvocationFailure(FailureCode.PROVIDER,
                        "provider execution failed");
                case CONFIG_PARSE_ERROR, CONFIG_SCHEMA_ERROR, PROVIDER_NOT_FOUND, PROVIDER_VERSION_MISMATCH,
                        PROVIDER_CONFIG_ERROR, GRAPH_REFERENCE_ERROR, GRAPH_CYCLE_ERROR, SCHEMA_INCOMPATIBLE,
                        DATASET_LIMIT_EXCEEDED -> new InvocationFailure(FailureCode.CONFIGURATION,
                        "application configuration failed");
            };
        }
    }
}
