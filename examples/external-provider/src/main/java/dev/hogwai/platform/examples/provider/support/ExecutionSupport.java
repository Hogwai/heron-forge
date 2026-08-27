package dev.hogwai.platform.examples.provider.support;

import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.execution.ExecutionContext;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Execution and input validation helpers shared by provider capabilities.
 */
public final class ExecutionSupport {

    private ExecutionSupport() {
    }

    public static void checkExecution(ExecutionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        context.cancellationToken().throwIfCancellationRequested();
        if (!Clock.systemUTC().instant().isBefore(context.deadline())) {
            throw new PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, List.of());
        }
    }

    /**
     * Checks the query-side cancellation and deadline contract.
     */
    public static void checkQuery(QueryContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (context.isCancellationRequested()) {
            throw new PlatformException(PlatformErrorCode.CANCELLATION_REQUESTED, List.of());
        }
        if (!Clock.systemUTC().instant().isBefore(context.deadline())) {
            throw new PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, List.of());
        }
    }
}
