package dev.hogwai.platform.spi.execution;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationTokenTest {

    @Test
    void throwIfCancellationRequestedDoesNothingWhenNotRequested() {
        CancellationToken token = () -> false;
        assertThatCode(token::throwIfCancellationRequested).doesNotThrowAnyException();
    }

    @Test
    void throwIfCancellationRequestedThrowsWhenRequested() {
        CancellationToken token = () -> true;
        assertThatThrownBy(token::throwIfCancellationRequested)
                .isInstanceOf(PlatformException.class)
                .hasFieldOrPropertyWithValue("code", PlatformErrorCode.CANCELLATION_REQUESTED);
    }
}
