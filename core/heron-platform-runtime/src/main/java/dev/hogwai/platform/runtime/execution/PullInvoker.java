package dev.hogwai.platform.runtime.execution;

import dev.hogwai.platform.runtime.compile.CapabilityNode;
import dev.hogwai.platform.runtime.compile.PortBinding;
import dev.hogwai.platform.runtime.snapshot.RuntimeSnapshot;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Package-private synchronous evaluator for one entrypoint dependency closure. */
public final class PullInvoker {

    private final Clock clock;

    public PullInvoker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PullInvoker() {
        this(Clock.systemUTC());
    }

    public MaterializedDataSet invokeTarget(RuntimeSnapshot snapshot, String targetId, ExecutionContext context) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return new Evaluator(clock).evaluate(snapshot, targetId, context, new HashMap<>());
    }

    private static final class Evaluator {
        private final RequestGuard guard;
        private final ProviderExecutor executor = new ProviderExecutor();

        private Evaluator(Clock clock) {
            this.guard = new RequestGuard(clock);
        }

        private MaterializedDataSet evaluate(RuntimeSnapshot snapshot, String id, ExecutionContext context,
                                             Map<String, MaterializedDataSet> memoized) {
            MaterializedDataSet cached = memoized.get(id);
            if (cached != null) {
                return cached;
            }
            CapabilityNode node = snapshot.graph().node(id).orElseThrow(Evaluator::configurationFailure);
            Map<PortId, MaterializedDataSet> inputs = new HashMap<>();
            for (PortBinding binding : node.inputs()) {
                guard.check(context);
                inputs.put(binding.inputPort(), evaluate(snapshot, binding.source().id(), context, memoized));
            }
            guard.check(context);
            MaterializedDataSet result = executor.execute(snapshot, node, CapabilityInputs.of(inputs), context);
            memoized.put(id, result);
            return result;
        }

        private static PlatformException configurationFailure() {
            return new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR, java.util.List.of(
                    new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR, null,
                            "configured target does not exist", "reference an existing capability")));
        }
    }

    private static final class RequestGuard {
        private final Clock clock;

        private RequestGuard(Clock clock) {
            this.clock = clock;
        }

        private void check(ExecutionContext context) {
            context.cancellationToken().throwIfCancellationRequested();
            if (!clock.instant().isBefore(context.deadline())) {
                throw new PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, java.util.List.of(
                        new Diagnostic(PlatformErrorCode.DEADLINE_EXCEEDED, Severity.ERROR, null,
                                "execution deadline exceeded", null)));
            }
        }
    }

    private static final class ProviderExecutor {
        private MaterializedDataSet execute(RuntimeSnapshot snapshot, CapabilityNode node,
                                             CapabilityInputs inputs, ExecutionContext context) {
            try (CapabilityInstance instance = snapshot.instance(node.id()).orElseThrow(ProviderExecutor::configurationFailure)) {
                MaterializedDataSet result = instance.execute(inputs, context);
                return Objects.requireNonNull(result, "provider returned a null dataset");
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
