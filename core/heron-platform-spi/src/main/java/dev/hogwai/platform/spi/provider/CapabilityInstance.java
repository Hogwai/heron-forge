package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.data.DataSet;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.execution.ExecutionContext;

/**
 * A single instance of a provider capability, ready to execute.
 *
 * <p>Execution is synchronous and returns a {@link DataSet}: implementations
 * choose their result shape through the covariant return —
 * {@link MaterializedDataSet} for in-memory
 * results, {@link StreamingDataSet} for lazy
 * bounded batches. The instance is {@link AutoCloseable}; {@link #close()} is a
 * no-op by default and may be overridden to release resources. When a
 * capability observes that its deadline has been exceeded or that cancellation
 * has been requested, it must fail with
 * {@link PlatformErrorCode#DEADLINE_EXCEEDED} or
 * {@link PlatformErrorCode#CANCELLATION_REQUESTED} respectively; no retry
 * semantics are implied. Framework-independent.
 */
public interface CapabilityInstance extends AutoCloseable {

    /**
     * Executes the capability against the given inputs.
     *
     * <p>If the execution deadline is exceeded or cancellation is requested, the
     * capability must fail with {@link PlatformErrorCode#DEADLINE_EXCEEDED} or
     * {@link PlatformErrorCode#CANCELLATION_REQUESTED} respectively.
     *
     * @param inputs  the capability inputs
     * @param context the execution context
     * @return the result dataset, materialized or streaming
     */
    DataSet execute(CapabilityInputs inputs, ExecutionContext context);

    /**
     * Releases resources held by this instance. No-op by default.
     */
    @Override
    default void close() {
        // no-op by default
    }
}
