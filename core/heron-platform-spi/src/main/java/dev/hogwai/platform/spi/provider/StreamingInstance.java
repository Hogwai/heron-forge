package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;

/**
 * Instance of a capability that streams its result instead of materializing it.
 *
 * <p>When a streaming capability is consumed as an upstream input, the runtime
 * collects its batches into a materialized dataset before handing it to the
 * consuming instance. When it is an entrypoint target, its result flows to the
 * host as-is. The returned dataset owns the per-invocation resources; closing
 * it is the release path.
 */
@FunctionalInterface
public interface StreamingInstance {

    /**
     * Executes the capability and returns a lazy bounded stream of records.
     *
     * @param inputs  the resolved, materialized input datasets by port
     * @param context the execution context carrying deadline and cancellation
     * @return the streaming data set; never {@code null}
     */
    StreamingDataSet execute(CapabilityInputs inputs, ExecutionContext context);
}
