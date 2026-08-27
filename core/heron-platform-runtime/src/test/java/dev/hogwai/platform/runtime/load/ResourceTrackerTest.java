package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.snapshot.SnapshotResourceTracker;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the runtime {@link SnapshotResourceTracker} implementing the SPI
 * {@link dev.hogwai.platform.spi.provider.ResourceTracker}.
 */
class ResourceTrackerTest {

    @Test
    void implementsSpiResourceTracker() {
        assertThat(new SnapshotResourceTracker())
                .isInstanceOf(dev.hogwai.platform.spi.provider.ResourceTracker.class);
    }

    @Test
    void registersAndClosesSingleResource() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("resource"));

        tracker.close();

        assertThat(closed).containsExactly("resource");
    }

    @Test
    void closesInReverseOrderOfRegistration() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("first"));
        tracker.register(() -> closed.add("second"));
        tracker.register(() -> closed.add("third"));

        tracker.close();

        assertThat(closed).containsExactly("third", "second", "first");
    }

    @Test
    void closeIsIdempotent() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();
        List<String> closed = new ArrayList<>();
        tracker.register(() -> closed.add("first"));

        tracker.close();
        tracker.close();

        assertThat(closed).containsExactly("first");
    }

    @Test
    void closeOnEmptyTrackerIsNoOp() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();

        tracker.close();
        tracker.close();

        assertThat(tracker).isNotNull();
    }

    @Test
    void registerAfterCloseThrowsPlatformException() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();
        tracker.close();

        assertThatThrownBy(() -> tracker.register(() -> {
        }))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> assertThat(((PlatformException) e).code())
                        .isEqualTo(PlatformErrorCode.CANCELLATION_REQUESTED));
    }

    @Test
    void rejectsNullResource() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();

        assertThatThrownBy(() -> tracker.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void closesAllResourcesDespiteFailures() {
        SnapshotResourceTracker tracker = new SnapshotResourceTracker();
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