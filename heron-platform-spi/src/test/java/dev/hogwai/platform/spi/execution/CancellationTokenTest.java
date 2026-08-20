package dev.hogwai.platform.spi.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import org.junit.jupiter.api.Test;

class CancellationTokenTest {

    @Test
    void reportsCancellationState() {
        assertThat(((CancellationToken) () -> true).isCancellationRequested()).isTrue();
        assertThat(((CancellationToken) () -> false).isCancellationRequested()).isFalse();
    }

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
