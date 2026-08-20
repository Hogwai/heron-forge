package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceTrackerTest {

    @Test
    void registerAcceptsAutoCloseable() {
        List<AutoCloseable> registered = new ArrayList<>();
        ResourceTracker tracker = registered::add;
        AutoCloseable resource = () -> { };
        tracker.register(resource);
        assertThat(registered).containsExactly(resource);
    }
}
