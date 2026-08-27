package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import dev.hogwai.platform.spi.provider.CapabilityInputs;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.util.List;

/**
 * Capability fixture recording close order and optional close failures.
 */
final class SnapshotBuilderTestInstance implements CapabilityInstance {

    private final String id;
    private final List<String> closeOrder;
    private final boolean closeThrows;
    private final String closeFailureMessage;
    private boolean closed;

    SnapshotBuilderTestInstance(String id, List<String> closeOrder, boolean closeThrows) {
        this(id, closeOrder, closeThrows, "close failed: " + id);
    }

    SnapshotBuilderTestInstance(String id, List<String> closeOrder, boolean closeThrows,
                                String closeFailureMessage) {
        this.id = id;
        this.closeOrder = closeOrder;
        this.closeThrows = closeThrows;
        this.closeFailureMessage = closeFailureMessage;
    }

    @Override
    public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
        throw new UnsupportedOperationException("not used in snapshot builder tests");
    }

    @Override
    public void close() {
        closed = true;
        closeOrder.add(id);
        if (closeThrows) {
            throw new IllegalStateException(closeFailureMessage);
        }
    }

    boolean isClosed() {
        return closed;
    }
}
