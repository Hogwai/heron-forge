package dev.hogwai.platform.runtime.execution;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.hogwai.platform.runtime.snapshot.SnapshotCandidate;
import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.execution.CancellationToken;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.FailureCode;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.host.StreamingPayload;

/**
 * Owner of one loaded candidate and its provider resources.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class RuntimeApplication implements HostApplication {

    private final SnapshotCandidate candidate;
    private final List<RuntimeEntrypoint> runtimeEntrypoints;
    private final List<EntrypointDescriptor> entrypoints;
    private final PullInvoker invoker;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RuntimeApplication(SnapshotCandidate candidate, List<RuntimeEntrypoint> entrypoints, Clock clock) {
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

    /**
     * {@inheritDoc}
     *
     * <p>Runs the graph exactly once and adapts the result shape for the host.
     */
    @Override
    public ExecutionOutcome execute(InvocationRequest request) {
        return executeOnce(request);
    }

    /**
     * Single-execution core: guards, entrypoint resolution and exactly one
     * target evaluation.
     */
    private ExecutionOutcome executeOnce(InvocationRequest request) {
        if (closed.get()) {
            return ExecutionOutcome.failure(new InvocationFailure(FailureCode.INTERNAL, "application is closed"));
        }
        if (request == null) {
            return ExecutionOutcome.failure(new InvocationFailure(FailureCode.INVALID_REQUEST, "invalid request"));
        }
        RuntimeEntrypoint entrypoint = find(request);
        if (entrypoint == null) {
            return ExecutionOutcome.failure(
                    new InvocationFailure(FailureCode.ENTRYPOINT_NOT_FOUND, "entrypoint not found"));
        }
        try {
            ExecutionContext context = new ExecutionContext(request.requestId(), candidate.snapshot().generationId(),
                    request.deadline(), cancellationToken(request), request.correlationId());
            DataSet result = invoker.invokeTarget(candidate.snapshot(), entrypoint.target(), context);
            if (result instanceof StreamingDataSet streamed) {
                return ExecutionOutcome.streaming(new StreamedPayload(streamed));
            }
            return ExecutionOutcome.materialized(StructuredPayloadProjector.project(result));
        } catch (PlatformException failure) {
            return ExecutionOutcome.failure(failure(failure.code()));
        } catch (RuntimeException _) {
            return ExecutionOutcome.failure(
                    new InvocationFailure(FailureCode.INTERNAL, "internal invocation failure"));
        }
    }

    /**
     * Adapts an SPI streaming dataset to the host-generic payload contract.
     */
    private record StreamedPayload(StreamingDataSet dataset) implements StreamingPayload {

        private StreamedPayload(StreamingDataSet dataset) {
            this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
        }

        @Override
        public Optional<List<Map<String, Object>>> nextBatch() {
            return dataset.nextBatch().map(batch -> batch.stream()
                    .map(StructuredPayloadProjector::toGenericRow)
                    .toList());
        }


        @Override
        public String schemaId() {
            return dataset.schema().identifier();
        }

        @Override
        public int schemaVersion() {
            return dataset.schema().version();
        }

        @Override
        public long deliveredRowCount() {
            return dataset.deliveredRowCount();
        }

        @Override
        public void close() {
            dataset.close();
        }
    }

    private RuntimeEntrypoint find(InvocationRequest request) {
        return runtimeEntrypoints.stream()
                .filter(snapshot -> snapshot.descriptor().id().equals(request.entrypointId()))
                .findFirst()
                .orElse(null);
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

    @SuppressWarnings("PMD.CyclomaticComplexity")
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
                     DATASET_LIMIT_EXCEEDED, DATA_ACCESS_UNAVAILABLE -> new InvocationFailure(FailureCode.CONFIGURATION,
                        "application configuration failed");
            };
        }
    }
}
