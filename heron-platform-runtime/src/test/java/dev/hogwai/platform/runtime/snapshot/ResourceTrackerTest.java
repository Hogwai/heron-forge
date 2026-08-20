package dev.hogwai.platform.runtime.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the runtime {@link ResourceTracker} implementing the SPI
 * {@link dev.hogwai.platform.spi.provider.ResourceTracker}.
 */
class ResourceTrackerTest {

    @Test
    void implementsSpiResourceTracker() {
        assertThat(new ResourceTracker())
                .isInstanceOf(dev.hogwai.platform.spi.provider.ResourceTracker.class);
    }

    @Test
    void registersAndClosesSingleResource() {
        ResourceTracker tracker = new ResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("resource"));

        tracker.close();

        assertThat(closed).containsExactly("resource");
    }

    @Test
    void closesInReverseOrderOfRegistration() {
        ResourceTracker tracker = new ResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("first"));
        tracker.register(() -> closed.add("second"));
        tracker.register(() -> closed.add("third"));

        tracker.close();

        assertThat(closed).containsExactly("third", "second", "first");
    }

    @Test
    void closeIsIdempotent() {
        ResourceTracker tracker = new ResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("first"));

        tracker.close();
        tracker.close();

        assertThat(closed).containsExactly("first");
    }

    @Test
    void closeOnEmptyTrackerIsNoOp() {
        ResourceTracker tracker = new ResourceTracker();

        tracker.close();
        tracker.close();

        assertThat(tracker).isNotNull();
    }

    @Test
    void registerAfterCloseThrowsPlatformException() {
        ResourceTracker tracker = new ResourceTracker();
        tracker.close();

        assertThatThrownBy(() -> tracker.register(() -> { }))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED));
    }

    @Test
    void rejectsNullResource() {
        ResourceTracker tracker = new ResourceTracker();

        assertThatThrownBy(() -> tracker.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void closesAllResourcesDespiteFailures() {
        ResourceTracker tracker = new ResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("first"));
        tracker.register(() -> {
            throw new IllegalStateException("second failed");
        });
        tracker.register(() -> closed.add("third"));
        tracker.register(() -> {
            throw new IllegalStateException("fourth failed");
        });

        assertThatThrownBy(tracker::close)
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR);
                    assertThat(pe.getCause()).isInstanceOf(IllegalStateException.class)
                            .hasMessage("fourth failed");
                    assertThat(pe.getSuppressed()).hasSize(1);
                    assertThat(pe.getSuppressed()[0]).isInstanceOf(IllegalStateException.class)
                            .hasMessage("second failed");
                });

        assertThat(closed).containsExactly("third", "first");
    }
}