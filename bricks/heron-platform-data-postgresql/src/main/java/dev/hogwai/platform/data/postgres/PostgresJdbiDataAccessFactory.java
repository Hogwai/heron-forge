package dev.hogwai.platform.data.postgres;

import dev.hogwai.platform.data.jdbi.JdbiDataAccessFactory;
import dev.hogwai.platform.data.jdbi.JdbiPoolOptions;
import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;

import java.util.List;

import org.jdbi.v3.postgres.PostgresPlugin;

/**
 * Opens PostgreSQL data access clients backed by the generic Jdbi implementation.
 */
@HeronService(value = DataAccessFactory.class, id = "data.postgres")
public final class PostgresJdbiDataAccessFactory implements DataAccessFactory {

    private final JdbiDataAccessFactory delegate =
            new JdbiDataAccessFactory(List.of(new PostgresPlugin()), JdbiPoolOptions.defaults());

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
