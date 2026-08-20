package dev.hogwai.platform.spi.observation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExecutionObserverTest {

    @Test
    void receivesEventWithoutReturnChannel() {
        PlatformEvent event = new PlatformEvent(PlatformEvent.SCHEMA_VERSION, PlatformEventType.SNAPSHOT_BUILT,
                new ProviderId("acme"), ProviderVersion.parse("1.0.0"), "cap", "snap", "req",
                PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null);
        AtomicReference<PlatformEvent> received = new AtomicReference<>();
        ExecutionObserver observer = received::set;
        observer.onEvent(event);
        assertThat(received.get()).isSameAs(event);
    }
}
