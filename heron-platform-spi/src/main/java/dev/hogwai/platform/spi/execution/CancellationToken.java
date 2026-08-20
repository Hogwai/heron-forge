package dev.hogwai.platform.spi.execution;

import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import java.util.List;

/**
 * Framework-independent cancellation token.
 *
 * <p>Implementations report whether cancellation has been requested.
 * {@link #throwIfCancellationRequested()} throws a {@link PlatformException}
 * with {@link PlatformErrorCode#CANCELLATION_REQUESTED} when cancellation has
 * been requested.
 */
public interface CancellationToken {

    /**
     * Returns whether cancellation has been requested.
     *
     * @return {@code true} if cancellation has been requested
     */
    boolean isCancellationRequested();

    /**
     * Throws a cancellation error if cancellation has been requested.
     *
     * @throws PlatformException with {@link PlatformErrorCode#CANCELLATION_REQUESTED}
     *                           if cancellation has been requested
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new PlatformException(PlatformErrorCode.CANCELLATION_REQUESTED, List.of());
        }
    }
}
