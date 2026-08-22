package dev.hogwai.platform.data.jdbi;

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

/** Opens generic Jdbi-backed data access clients. */
public final class JdbiDataAccessFactory implements DataAccessFactory {

    private final List<JdbiPlugin> plugins;

    /** Creates a factory without any database-specific Jdbi plugins. */
    public JdbiDataAccessFactory() {
        this.plugins = List.of();
    }

    /**
     * Creates a factory that installs the supplied Jdbi plugins before probing a database.
     *
     * @param plugins plugins to install, in installation order
     */
    public JdbiDataAccessFactory(JdbiPlugin... plugins) {
        Objects.requireNonNull(plugins, "plugins must not be null");
        this.plugins = List.of(plugins);
    }

    /**
     * Opens a client after checking that the configured database is reachable.
     *
     * @param configuration the database configuration
     * @return a thread-safe data access client
     * @throws PlatformException if the startup probe fails
     */
    @Override
    public DataAccess open(DataAccessConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        try {
            Jdbi jdbi = Jdbi.create(configuration.url(), configuration.username(), configuration.password());
            plugins.forEach(jdbi::installPlugin);
            jdbi.withHandle(handle -> {
                handle.createQuery("SELECT 1").mapTo(Integer.class).one();
                return null;
            });
            return new JdbiDataAccess(jdbi);
        } catch (RuntimeException _) {
            throw startupFailure();
        }
    }

    private static PlatformException startupFailure() {
        Diagnostic diagnostic = new Diagnostic(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR, null,
                "Database startup probe failed.", "verify database availability");
        return new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of(diagnostic));
    }
}
