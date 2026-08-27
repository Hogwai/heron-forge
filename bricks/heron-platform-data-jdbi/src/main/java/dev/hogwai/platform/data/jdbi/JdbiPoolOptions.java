package dev.hogwai.platform.data.jdbi;

/**
 * Tuning for the optional HikariCP connection pool backing a data access
 * client. Brick-local on purpose: the SPI {@code DataAccessConfiguration}
 * carries only credentials, so pooling never leaks into the framework
 * contracts.
 *
 * @param enabled                 whether clients opened by the factory use a pool
 * @param maximumPoolSize         upper bound of pooled connections
 * @param minimumIdle             idle connections kept ready
 * @param connectionTimeoutMillis maximum wait for a pooled connection
 * @param maxLifetimeMillis       lifetime of a pooled connection
 */
public record JdbiPoolOptions(boolean enabled,
                              int maximumPoolSize,
                              int minimumIdle,
                              long connectionTimeoutMillis,
                              long maxLifetimeMillis) {

    /**
     * Creates options, rejecting incoherent pool sizing.
     */
    public JdbiPoolOptions {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be positive");
        }
        if (minimumIdle < 0) {
            throw new IllegalArgumentException("minimumIdle must not be negative");
        }
        if (minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException("minimumIdle must not exceed maximumPoolSize");
        }
        if (connectionTimeoutMillis < 1) {
            throw new IllegalArgumentException("connectionTimeoutMillis must be positive");
        }
        if (maxLifetimeMillis < 1) {
            throw new IllegalArgumentException("maxLifetimeMillis must be positive");
        }
    }

    /**
     * Returns options that bypass pooling: every handle opens its own
     * connection.
     *
     * @return non-pooling options
     */
    public static JdbiPoolOptions disabled() {
        return new JdbiPoolOptions(false, 1, 0, 5_000L, 1_800_000L);
    }

    /**
     * Returns conservative defaults suited to a per-capability client: a small
     * pool that grows on demand.
     *
     * @return default pooling options
     */
    public static JdbiPoolOptions defaults() {
        return new JdbiPoolOptions(true, 10, 1, 5_000L, 1_800_000L);
    }
}
