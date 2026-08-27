package dev.hogwai.platform.spi.provider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTrackerTest {

    @Test
    void registerAcceptsAutoCloseable() {
        List<AutoCloseable> registered = new ArrayList<>();
        ResourceTracker tracker = registered::add;
        AutoCloseable resource = () -> {
        };
        tracker.register(resource);
        assertThat(registered).containsExactly(resource);
    }
}
