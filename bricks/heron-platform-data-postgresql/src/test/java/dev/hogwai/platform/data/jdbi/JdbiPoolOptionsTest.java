package dev.hogwai.platform.data.jdbi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Verifies pool option defaults and incoherent-size rejection. */
class JdbiPoolOptionsTest {

    @Test
    void exposesConservativeDefaults() {
        JdbiPoolOptions options = JdbiPoolOptions.defaults();

        assertThat(options.enabled()).isTrue();
        assertThat(options.maximumPoolSize()).isEqualTo(10);
        assertThat(options.minimumIdle()).isEqualTo(1);
        assertThat(options.connectionTimeoutMillis()).isEqualTo(5_000L);
        assertThat(options.maxLifetimeMillis()).isEqualTo(1_800_000L);
    }

    @Test
    void disablesPoolingExplicitly() {
        assertThat(JdbiPoolOptions.disabled().enabled()).isFalse();
    }

    @Test
    void rejectsNonPositiveMaximumPoolSize() {
        assertThatThrownBy(() -> new JdbiPoolOptions(true, 0, 0, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximumPoolSize must be positive");
    }

    @Test
    void rejectsNegativeMinimumIdle() {
        assertThatThrownBy(() -> new JdbiPoolOptions(true, 10, -1, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minimumIdle must not be negative");
    }

    @Test
    void rejectsMinimumIdleAboveMaximum() {
        assertThatThrownBy(() -> new JdbiPoolOptions(true, 2, 3, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minimumIdle must not exceed maximumPoolSize");
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThatThrownBy(() -> new JdbiPoolOptions(true, 10, 1, 0L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectionTimeoutMillis must be positive");
        assertThatThrownBy(() -> new JdbiPoolOptions(true, 10, 1, 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxLifetimeMillis must be positive");
    }
}
