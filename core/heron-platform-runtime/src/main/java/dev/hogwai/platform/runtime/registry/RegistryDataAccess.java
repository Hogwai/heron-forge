package dev.hogwai.platform.runtime.registry;

import java.util.List;
import java.util.ServiceLoader;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;

/**
 * Discovers the {@link DataAccessFactory} implementation on the trusted
 * classpath via {@link ServiceLoader}, mirroring the runtime loading path.
 *
 * <p>When no implementation is available, a factory that fails on
 * {@code open} is returned so configurations that never touch data access
 * still load and activate.
 */
final class RegistryDataAccess {

    private RegistryDataAccess() {
        // no instances
    }

    /**
     * Returns the discovered data access factory, or an unavailable factory.
     *
     * @return the discovered factory, or one that fails on {@code open}
     */
    static DataAccessFactory discover() {
        return ServiceLoader.load(DataAccessFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseGet(RegistryDataAccess::unavailable);
    }

    private static DataAccessFactory unavailable() {
        return _ -> {
            throw new PlatformException(PlatformErrorCode.DATA_ACCESS_UNAVAILABLE,
                    List.of(new Diagnostic(PlatformErrorCode.DATA_ACCESS_UNAVAILABLE, Severity.ERROR, null,
                            "no DataAccessFactory implementation found on the classpath; "
                                    + "add a data brick such as heron-platform-data-postgresql",
                            null)));
        };
    }
}
