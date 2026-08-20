package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.data.postgres.PostgresJdbiDataAccessFactory;
import dev.hogwai.platform.host.api.EntrypointDescriptor;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.runtime.load.config.ParsedApplication;
import dev.hogwai.platform.runtime.load.config.SafeYamlParser;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.Severity;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public Core boundary for loading a framework-independent application. */
public final class ApplicationLoader {

    private ApplicationLoader() {
        // no instances
    }

    /**
     * Loads an application using providers discovered on the trusted classpath.
     *
     * @param yaml YAML configuration input
     * @return the loaded host application
     */
    public static HostApplication load(InputStream yaml) {
        return load(yaml, new dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry(),
                new PostgresJdbiDataAccessFactory());
    }

    /**
     * Loads an application using the supplied registry, primarily for composition and tests.
     *
     * @param yaml     YAML configuration input
     * @param registry provider registry to use
     * @return the loaded host application
     */
    static HostApplication load(InputStream yaml, ProviderRegistry registry) {
        return load(yaml, registry, new PostgresJdbiDataAccessFactory());
    }

    /**
     * Loads an application using supplied registry and data access factory.
     * This composition seam is package-private for runtime tests.
     */
    static HostApplication load(InputStream yaml, ProviderRegistry registry,
                                DataAccessFactory dataAccessFactory) {
        Objects.requireNonNull(yaml, "yaml must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(dataAccessFactory, "dataAccessFactory must not be null");
        ParsedApplication parsed = new SafeYamlParser().parse(yaml);
        if (!parsed.isValid()) {
            throw new PlatformException(firstErrorCode(parsed), parsed.diagnostics());
        }

        SnapshotBuilder builder = new SnapshotBuilder(new ProviderResolver(registry),
                new GraphCompiler(), Clock.systemUTC(), dataAccessFactory, () -> UUID.randomUUID().toString());
        SnapshotCandidate candidate = builder.build(parsed.application());
        try {
            List<RuntimeEntrypoint> entrypoints = runtimeEntrypoints(parsed);
            EntrypointValidator.validate(candidate, entrypoints);
            return new RuntimeApplication(candidate, entrypoints, Clock.systemUTC());
        } catch (RuntimeException failure) {
            closeQuietly(candidate);
            throw failure;
        }
    }

    private static List<RuntimeEntrypoint> runtimeEntrypoints(ParsedApplication parsed) {
        return parsed.application().entrypoints().stream()
                .map(entrypoint -> new RuntimeEntrypoint(
                        new EntrypointDescriptor(entrypoint.id(), entrypoint.path()), entrypoint.target()))
                .toList();
    }

    private static PlatformErrorCode firstErrorCode(ParsedApplication parsed) {
        return parsed.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                .map(Diagnostic::code)
                .findFirst()
                .orElse(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }

    private static void closeQuietly(SnapshotCandidate candidate) {
        try {
            candidate.close();
        } catch (RuntimeException ignored) {
            // The primary configuration/build failure is the public result.
        }
    }

    private static final class EntrypointValidator {
        private EntrypointValidator() {
            // no instances
        }

        static void validate(SnapshotCandidate candidate, List<RuntimeEntrypoint> entrypoints) {
            for (RuntimeEntrypoint entrypoint : entrypoints) {
                if (candidate.snapshot().graph().node(entrypoint.target()).isEmpty()) {
                    throw new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR, List.of(
                            new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                    "/spec/entrypoints", "entrypoint target does not exist",
                                    "reference an existing capability")));
                }
            }
        }
    }

}
