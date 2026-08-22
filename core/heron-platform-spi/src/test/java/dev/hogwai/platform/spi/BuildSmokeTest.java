package dev.hogwai.platform.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSmokeTest {

    @Test
    void runsOnJava25OrNewer() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
    }
}
