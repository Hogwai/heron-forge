package dev.hogwai.platform.data.jdbi;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;

import java.util.List;
import java.util.Objects;

/**
 * Opens generic Jdbi-backed data access clients.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class JdbiDataAccessFactory implements DataAccessFactory {

    private final List<JdbiPlugin> plugins;
    private final JdbiPoolOptions poolOptions;

    /**
     * Creates a factory without any database-specific Jdbi plugins and without pooling.
     */
    public JdbiDataAccessFactory() {
        this(List.of(), JdbiPoolOptions.disabled());
    }

    /**
     * Creates a factory that installs the supplied Jdbi plugins before probing a database.
     *
     * @param plugins plugins to install, in installation order
     */
    public JdbiDataAccessFactory(JdbiPlugin... plugins) {
        this(List.of(plugins), JdbiPoolOptions.disabled());
    }

    /**
     * Creates a factory that installs the supplied plugins and pools connections
     * according to the supplied options.
     *
     * @param plugins     plugins to install, in installation order
     * @param poolOptions pooling behavior for opened clients
     */
    public JdbiDataAccessFactory(List<JdbiPlugin> plugins, JdbiPoolOptions poolOptions) {
        Objects.requireNonNull(plugins, "plugins must not be null");
        Objects.requireNonNull(poolOptions, "poolOptions must not be null");
        this.plugins = List.copyOf(plugins);
        this.poolOptions = poolOptions;
    }

    /**
     * Opens a client after checking that the configured database is reachable.
     *
     * <p>With pooling enabled, one {@link HikariDataSource} backs the client and
     * is closed together with it; otherwise each handle opens its own
     * connection.
     *
     * @param configuration the database configuration
     * @return a thread-safe data access client
     * @throws PlatformException if the startup probe fails
     */
    @Override
    public DataAccess open(DataAccessConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return poolOptions.enabled() ? openPooled(configuration) : openUnpooled(configuration);
    }

    private DataAccess openPooled(DataAccessConfiguration configuration) {
        HikariDataSource dataSource = null;
        try {
            dataSource = new HikariDataSource(hikariConfig(configuration));
            Jdbi jdbi = Jdbi.create(dataSource);
            plugins.forEach(jdbi::installPlugin);
            probe(jdbi);
            return new JdbiDataAccess(jdbi, dataSource);
        } catch (RuntimeException _) {
            closeQuietly(dataSource);
            throw startupFailure();
        }
    }

    private DataAccess openUnpooled(DataAccessConfiguration configuration) {
        try {
            Jdbi jdbi = Jdbi.create(configuration.url(), configuration.username(), configuration.password());
            plugins.forEach(jdbi::installPlugin);
            probe(jdbi);
            return new JdbiDataAccess(jdbi);
        } catch (RuntimeException _) {
            throw startupFailure();
        }
    }

    private HikariConfig hikariConfig(DataAccessConfiguration configuration) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(configuration.url());
        config.setUsername(configuration.username());
        config.setPassword(configuration.password());
        config.setMaximumPoolSize(poolOptions.maximumPoolSize());
        config.setMinimumIdle(poolOptions.minimumIdle());
        config.setConnectionTimeout(poolOptions.connectionTimeoutMillis());
        config.setMaxLifetime(poolOptions.maxLifetimeMillis());
        return config;
    }

    private static void probe(Jdbi jdbi) {
        jdbi.withHandle(handle -> {
            handle.createQuery("SELECT 1").mapTo(Integer.class).one();
            return null;
        });
    }

    private static void closeQuietly(HikariDataSource dataSource) {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static PlatformException startupFailure() {
        Diagnostic diagnostic = new Diagnostic(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR, null,
                "Database startup probe failed.", "verify database availability");
        return new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of(diagnostic));
    }
}
