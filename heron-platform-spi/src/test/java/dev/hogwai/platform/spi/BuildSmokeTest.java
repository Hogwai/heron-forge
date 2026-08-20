package dev.hogwai.platform.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {

    @Test
    void runsOnJava25OrNewer() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
    }
}
