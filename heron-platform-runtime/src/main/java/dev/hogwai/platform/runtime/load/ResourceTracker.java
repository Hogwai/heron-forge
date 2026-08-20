package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.Severity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Runtime implementation of the SPI
 * {@link dev.hogwai.platform.spi.provider.ResourceTracker}.
 *
 * <p>Registers {@link AutoCloseable} resources and closes them in reverse order
 * of registration when {@link #close()} is invoked. Closing is idempotent. If
 * one or more resources fail to close, the remaining resources are still
 * closed; the first failure is reported as the cause of a
 * {@link PlatformException} carrying
 * {@link PlatformErrorCode#CAPABILITY_EXECUTION_ERROR} (the chosen stable code
 * for resource close failures) and every subsequent failure is attached as a
 * suppressed exception.
 *
 * <p>This class is not thread-safe; it is intended for single-threaded use
 * within a build/teardown sequence. It is distinct from the SPI interface it
 * implements and does not modify it.
 */
final class ResourceTracker implements dev.hogwai.platform.spi.provider.ResourceTracker {

    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean closed;

    /**
     * Registers a resource to be closed with the tracker.
     *
     * @param resource the resource to register
     * @throws NullPointerException if {@code resource} is {@code null}
     * @throws PlatformException    if the tracker is already closed
     */
    @Override
    public void register(AutoCloseable resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        if (closed) {
            throw lifecycleViolation("cannot register a resource on a closed tracker");
        }
        resources.push(resource);
    }

    /**
     * Closes all registered resources in reverse order of registration.
     *
     * <p>Idempotent: subsequent calls are no-ops. All resources are closed even
     * if some fail; failures are aggregated as described in the class javadoc.
     *
     * @throws PlatformException if one or more resources failed to close
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        PlatformException failure = null;
        while (!resources.isEmpty()) {
            AutoCloseable resource = resources.pop();
            try {
                resource.close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of(
                            new Diagnostic(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR, null,
                                    "a registered resource failed to close", null)), e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static PlatformException lifecycleViolation(String message) {
        return new PlatformException(PlatformErrorCode.CANCELLATION_REQUESTED, List.of(
                new Diagnostic(PlatformErrorCode.CANCELLATION_REQUESTED, Severity.ERROR, null, message, null)));
    }
}
