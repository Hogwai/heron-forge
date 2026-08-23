package dev.hogwai.platform.runtime.registry;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.execution.RuntimeApplication;
import dev.hogwai.platform.runtime.execution.RuntimeEntrypoint;
import dev.hogwai.platform.runtime.snapshot.SnapshotBuilder;
import dev.hogwai.platform.runtime.snapshot.SnapshotCandidate;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.registry.GenerationRecord;

/**
 * Wires a parsed application and a sealed generation id into a live
 * {@link RuntimeApplication}.
 *
 * <p>This mirrors the minimal composition of the runtime loading path:
 * {@link SnapshotBuilder} compiles the graph and creates one provider instance
 * per node with the generationRecord's generation id injected through the generation id
 * supplier, declared entrypoints are validated against the compiled graph, and
 * already-created resources are torn down if any step fails.
 */
final class RuntimeApplicationFactory {

    private final Clock clock;
    private final ProviderRegistry registry;
    private final DataAccessFactory dataAccessFactory;

    /**
     * Creates a factory with explicit collaborators.
     *
     * @param clock             the clock exposed to providers and to the invoker
     * @param registry          the provider registry to compile against
     * @param dataAccessFactory the data access factory exposed to providers
     * @throws NullPointerException if any argument is {@code null}
     */
    RuntimeApplicationFactory(Clock clock, ProviderRegistry registry, DataAccessFactory dataAccessFactory) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.dataAccessFactory =
                Objects.requireNonNull(dataAccessFactory, "dataAccessFactory must not be null");
    }

    /**
     * Builds the live application for an already validated parsed configuration.
     *
     * @param parsed the validated parsed application
     * @param generationRecord the sealed generation generationRecord supplying the generation id
     * @return the live application owning its provider resources
     * @throws PlatformException when compilation, instance creation or
     *                           entrypoint validation fails; already-created
     *                           resources are closed before the failure surfaces
     */
    RuntimeApplication build(ParsedApplication parsed, GenerationRecord generationRecord) {
        SnapshotBuilder builder = new SnapshotBuilder(new ProviderResolver(registry), new GraphCompiler(),
                clock, dataAccessFactory, generationRecord::generationId);
        SnapshotCandidate candidate = builder.build(parsed.application());
        try {
            List<Diagnostic> entrypointDiagnostics = RegistryEntrypointValidator.validate(
                    candidate.snapshot().graph(), parsed.application().entrypoints());
            if (!entrypointDiagnostics.isEmpty()) {
                throw new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR, entrypointDiagnostics);
            }
            return new RuntimeApplication(candidate, runtimeEntrypoints(parsed), clock);
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

    private static void closeQuietly(SnapshotCandidate candidate) {
        try {
            candidate.close();
        } catch (RuntimeException _) {
            // The primary activation failure is the public result.
        }
    }
}
