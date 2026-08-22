package dev.hogwai.platform.runtime.load;

import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.runtime.config.EntrypointConfig;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.runtime.execution.RuntimeApplication;
import dev.hogwai.platform.runtime.execution.RuntimeEntrypoint;
import dev.hogwai.platform.runtime.snapshot.SnapshotBuilder;
import dev.hogwai.platform.runtime.snapshot.SnapshotCandidate;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.HostApplication;

/** Public Core boundary for loading a framework-independent application. */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class ApplicationLoader {

    public static final String YAML_MUST_NOT_BE_NULL = "yaml must not be null";

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
                discoverDataAccessFactory());
    }

    /**
     * Validates an application using providers discovered on the trusted
     * classpath, without creating provider instances or runtime resources.
     *
     * @param yaml YAML configuration input
     * @return the immutable validation report
     */
    public static ValidationReport validate(InputStream yaml) {
        Objects.requireNonNull(yaml, YAML_MUST_NOT_BE_NULL);
        try {
            return validate(yaml, new dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry());
        } catch (PlatformException failure) {
            return new ValidationReport(failure.diagnostics());
        }
    }

    /**
     * Validates an application using the supplied provider registry, primarily
     * for composition and tests. This method never creates provider instances.
     *
     * @param yaml     YAML configuration input
     * @param registry provider registry to use
     * @return the immutable validation report
     */
    static ValidationReport validate(InputStream yaml, ProviderRegistry registry) {
        Objects.requireNonNull(yaml, YAML_MUST_NOT_BE_NULL);
        Objects.requireNonNull(registry, "registry must not be null");

        ParsedApplication parsed = new SafeYamlParser().parse(yaml);
        if (!parsed.isValid()) {
            return new ValidationReport(parsed.diagnostics());
        }

        List<EntrypointConfig> entrypoints = parsed.application().entrypoints();
        GraphCompiler.CompilationResult compilation;
        try {
            compilation = new GraphCompiler().compileWithDiagnostics(parsed.application(), new ProviderResolver(registry));
        } catch (PlatformException failure) {
            List<Diagnostic> diagnostics = new ArrayList<>(failure.diagnostics());
            diagnostics.addAll(EntrypointValidator.validate(
                    declaredCapabilityIds(parsed), entrypoints));
            return new ValidationReport(diagnostics);
        }

        List<Diagnostic> diagnostics = new ArrayList<>(compilation.diagnostics());
        diagnostics.addAll(EntrypointValidator.validate(compilation.graph(), entrypoints));
        return new ValidationReport(diagnostics);
    }

    private static Set<String> declaredCapabilityIds(ParsedApplication parsed) {
        List<CapabilityConfig> capabilities = parsed.application().capabilities();
        Set<String> ids = LinkedHashSet.newLinkedHashSet(capabilities.size());
        capabilities.forEach(capability -> ids.add(capability.id()));
        return ids;
    }

    /**
     * Loads an application using the supplied registry, primarily for composition and tests.
     *
     * @param yaml     YAML configuration input
     * @param registry provider registry to use
     * @return the loaded host application
     */
    static HostApplication load(InputStream yaml, ProviderRegistry registry) {
        return load(yaml, registry, discoverDataAccessFactory());
    }

    /**
     * Discovers the {@link DataAccessFactory} implementation on the trusted
     * classpath via {@link ServiceLoader}. The core is agnostic of any concrete
     * database; a data brick (e.g. PostgreSQL) provides the implementation.
     *
     * <p>When no implementation is available, a factory that fails on
     * {@code open} is returned so that configurations that never touch data
     * access still load.
     *
     * @return the discovered data access factory, or an unavailable factory
     */
    private static DataAccessFactory discoverDataAccessFactory() {
        return ServiceLoader.load(DataAccessFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseGet(ApplicationLoader::unavailableDataAccessFactory);
    }

    private static DataAccessFactory unavailableDataAccessFactory() {
        return _ -> {
            throw new PlatformException(PlatformErrorCode.DATA_ACCESS_UNAVAILABLE,
                    List.of(new Diagnostic(PlatformErrorCode.DATA_ACCESS_UNAVAILABLE, Severity.ERROR, null,
                            "no DataAccessFactory implementation found on the classpath; "
                                    + "add a data brick such as heron-platform-data-postgresql",
                            null)));
        };
    }

    /**
     * Loads an application using supplied registry and data access factory.
     * This composition seam is package-private for runtime tests.
     */
    static HostApplication load(InputStream yaml, ProviderRegistry registry,
                                DataAccessFactory dataAccessFactory) {
        Objects.requireNonNull(yaml, YAML_MUST_NOT_BE_NULL);
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
            List<Diagnostic> entrypointDiagnostics = EntrypointValidator.validate(
                    candidate.snapshot().graph(), parsed.application().entrypoints());
            if (!entrypointDiagnostics.isEmpty()) {
                throw new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR, entrypointDiagnostics);
            }
            List<RuntimeEntrypoint> entrypoints = runtimeEntrypoints(parsed);
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
        } catch (RuntimeException _) {
            // The primary configuration/build failure is the public result.
        }
    }

    private static final class EntrypointValidator {
        private EntrypointValidator() {
            // no instances
        }

        static List<Diagnostic> validate(CapabilityGraph graph, List<EntrypointConfig> entrypoints) {
            return validate(graph.nodeIds(), entrypoints);
        }

        static List<Diagnostic> validate(Set<String> capabilityIds, List<EntrypointConfig> entrypoints) {
            List<Diagnostic> diagnostics = new ArrayList<>();
            for (int i = 0; i < entrypoints.size(); i++) {
                if (!capabilityIds.contains(entrypoints.get(i).target())) {
                    diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                            "/endpoints/" + i + "/target", "endpoint target does not exist",
                            "reference an existing capability"));
                }
            }
            return diagnostics;
        }
    }

}
