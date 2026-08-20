package dev.hogwai.platform.data.postgres;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.data.jdbi.JdbiDataAccessFactory;
import org.jdbi.v3.postgres.PostgresPlugin;

/** Opens PostgreSQL data access clients backed by the generic Jdbi implementation. */
public final class PostgresJdbiDataAccessFactory implements DataAccessFactory {

    private final JdbiDataAccessFactory delegate = new JdbiDataAccessFactory(new PostgresPlugin());

    /**
     * Opens a PostgreSQL data access client using the standard PostgreSQL Jdbi plugin.
     *
     * @param configuration the database configuration
     * @return a thread-safe data access client
     */
    @Override
    public DataAccess open(DataAccessConfiguration configuration) {
        return delegate.open(configuration);
    }
}
