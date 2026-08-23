package dev.hogwai.platform.runtime.execution;

import dev.hogwai.platform.runtime.compile.CapabilityNode;
import dev.hogwai.platform.runtime.compile.PortBinding;
import dev.hogwai.platform.runtime.snapshot.RuntimeSnapshot;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Package-private synchronous evaluator for one entrypoint dependency closure.
 *
 * <p>Upstream nodes are always collected into {@link MaterializedDataSet}s so
 * transforms can compose them; only the entrypoint target's own result keeps
 * its declared shape — a target returning a {@link StreamingDataSet} flows to
 * the caller lazily.
 */
public final class PullInvoker {

    private final Clock clock;

    public PullInvoker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PullInvoker() {
        this(Clock.systemUTC());
    }

    /**
     * Evaluates the dependency closure of the target and returns its result in
     * the shape the target capability declared.
     *
     * @param snapshot the runtime snapshot
     * @param targetId the entrypoint target capability id
     * @param context  the execution context
     * @return the target result, materialized or streaming
     */
    public DataSet invokeTarget(RuntimeSnapshot snapshot, String targetId, ExecutionContext context) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return new Evaluator(clock).evaluateTarget(snapshot, targetId, context);
    }

    /**
     * Evaluates the dependency closure and returns the target result forced
     * into a materialized dataset, whatever shape the target declared.
     *
     * @param snapshot the runtime snapshot
     * @param targetId the entrypoint target capability id
     * @param context  the execution context
     * @return the target result, materialized
     */
    public MaterializedDataSet invokeTargetAsMaterialized(RuntimeSnapshot snapshot, String targetId,
            ExecutionContext context) {
        return Evaluator.asMaterialized(invokeTarget(snapshot, targetId, context));
    }

    private static final class Evaluator {
        private final RequestGuard guard;
        private final ProviderExecutor executor = new ProviderExecutor();
        private final Map<String, MaterializedDataSet> memoized = new HashMap<>();

        private Evaluator(Clock clock) {
            this.guard = new RequestGuard(clock);
        }

        private DataSet evaluateTarget(RuntimeSnapshot snapshot, String id, ExecutionContext context) {
            CapabilityNode node = snapshot.graph().node(id).orElseThrow(Evaluator::configurationFailure);
            Map<PortId, MaterializedDataSet> inputs = new HashMap<>();
            for (PortBinding binding : node.inputs()) {
                guard.check(context);
                inputs.put(binding.inputPort(), evaluateMaterialized(snapshot, binding.source().id(), context));
            }
            guard.check(context);
            return executor.execute(snapshot, node, CapabilityInputs.of(inputs), context);
        }

        private MaterializedDataSet evaluateMaterialized(RuntimeSnapshot snapshot, String id,
                ExecutionContext context) {
            MaterializedDataSet cached = memoized.get(id);
            if (cached != null) {
                return cached;
            }
            CapabilityNode node = snapshot.graph().node(id).orElseThrow(Evaluator::configurationFailure);
            Map<PortId, MaterializedDataSet> inputs = new HashMap<>();
            for (PortBinding binding : node.inputs()) {
                guard.check(context);
                inputs.put(binding.inputPort(), evaluateMaterialized(snapshot, binding.source().id(), context));
            }
            guard.check(context);
            MaterializedDataSet result =
                    Evaluator.asMaterialized(executor.execute(snapshot, node, CapabilityInputs.of(inputs), context));
            memoized.put(id, result);
            return result;
        }

        private static MaterializedDataSet asMaterialized(DataSet result) {
            Objects.requireNonNull(result, "provider returned a null dataset");
            if (result instanceof MaterializedDataSet materialized) {
                return materialized;
            }
            if (result instanceof StreamingDataSet streamed) {
                return streamed.toMaterialized();
            }
            throw providerFailure();
        }

        private static PlatformException configurationFailure() {
            return new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR, List.of(
                    new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR, null,
                            "configured target does not exist", "reference an existing capability")));
        }

        private static PlatformException providerFailure() {
            return new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of(
                    new Diagnostic(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR, null,
                            "capability execution failed", "check the provider implementation")));
        }
    }

    private record RequestGuard(Clock clock) {

        private void check(ExecutionContext context) {
                context.cancellationToken().throwIfCancellationRequested();
                if (!clock.instant().isBefore(context.deadline())) {
                    throw new PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, List.of(
                            new Diagnostic(PlatformErrorCode.DEADLINE_EXCEEDED, Severity.ERROR, null,
                                    "execution deadline exceeded", null)));
                }
            }
        }

    private static final class ProviderExecutor {
        private DataSet execute(RuntimeSnapshot snapshot, CapabilityNode node,
                CapabilityInputs inputs, ExecutionContext context) {
            try (CapabilityInstance instance =
                    snapshot.instance(node.id()).orElseThrow(ProviderExecutor::configurationFailure)) {
                return Objects.requireNonNull(instance.execute(inputs, context),
                        "provider returned a null dataset");
            } catch (PlatformException failure) {
                if (failure.code() == PlatformErrorCode.DEADLINE_EXCEEDED
                        || failure.code() == PlatformErrorCode.CANCELLATION_REQUESTED) {
                    throw failure;
                }
                throw providerFailure();
            } catch (RuntimeException _) {
                throw providerFailure();
            }
        }

        private static PlatformException configurationFailure() {
            return new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR, java.util.List.of());
        }

        private static PlatformException providerFailure() {
            return new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, java.util.List.of(
                    new Diagnostic(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR, null,
                            "capability execution failed", "check the provider implementation")));
        }
    }
}
