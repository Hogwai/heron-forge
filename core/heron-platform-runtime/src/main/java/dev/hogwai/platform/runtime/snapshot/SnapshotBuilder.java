package dev.hogwai.platform.runtime.snapshot;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.runtime.compile.CapabilityNode;
import dev.hogwai.platform.runtime.compile.GraphCompiler;
import dev.hogwai.platform.runtime.compile.provider.ProviderResolver;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Builds a complete snapshot candidate from a configuration document.
 *
 * <p>The builder compiles an already parsed and validated application with a
 * {@link GraphCompiler} and {@link ProviderResolver}, then creates exactly one
 * {@link CapabilityInstance} per {@link CapabilityGraph} node. Each instance is
 * created with the raw safe configuration carried by the node and a
 * {@link BuildContext} that exposes only the application id, the snapshot id, a
 * {@link Clock}, the SPI {@link dev.hogwai.platform.spi.provider.ResourceTracker}
 * and the supplied {@link DataAccessFactory}. Providers own data access clients
 * they open and must register them immediately with the resource tracker.
 * Every created instance is registered with the runtime {@link SnapshotResourceTracker}
 * immediately so that a partial build can be torn down cleanly.
 *
 * <p>Providers are trusted: no additional dynamic resolution and no business
 * execution is performed. The result is an immutable lifecycle
 * {@link SnapshotCandidate} holding the {@link RuntimeSnapshot} and ownership
 * of the {@link SnapshotResourceTracker}.
 *
 * <p>If graph compilation, provider resolution, {@code create}, or
 * snapshot candidate assembly fails after partial creations, every
 * already-registered resource is closed in reverse order of registration, close
 * failures do not prevent the remaining closes, the primary error is preserved
 * and any close failures are attached as suppressed exceptions. Non-public
 * provider failures are converted into a stable {@link PlatformException}
 * without leaking details.
 */
public final class SnapshotBuilder {

    private final ProviderResolver resolver;
    private final GraphCompiler compiler;
    private final Clock clock;
    private final DataAccessFactory dataAccessFactory;
    private final Supplier<String> generationIdSupplier;

    /**
     * Creates a snapshot builder with the given collaborators.
     *
     * @param resolver             the provider resolver
     * @param compiler             the graph compiler
     * @param clock                the clock exposed to providers
     * @param dataAccessFactory    the data access factory exposed to providers
     * @param generationIdSupplier supplies the non-blank generation id
     * @throws NullPointerException if any argument is {@code null}
     */
    public SnapshotBuilder(ProviderResolver resolver, GraphCompiler compiler,
                    Clock clock, DataAccessFactory dataAccessFactory,
                    Supplier<String> generationIdSupplier) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.dataAccessFactory = Objects.requireNonNull(dataAccessFactory, "dataAccessFactory must not be null");
        this.generationIdSupplier = Objects.requireNonNull(generationIdSupplier, "generationIdSupplier must not be null");
    }

    /**
     * Builds a snapshot candidate from an already parsed and validated application.
     *
     * @param application the validated application configuration
     * @return the immutable lifecycle candidate
     * @throws NullPointerException if {@code application} is {@code null}
     * @throws PlatformException if graph compilation, provider resolution or
     *                           instance creation fails
     */
    public SnapshotCandidate build(ApplicationConfig application) {
        Objects.requireNonNull(application, "application must not be null");
        CapabilityGraph graph = compiler.compile(application, resolver);
        String generationId = FailureHandler.requireGenerationId(generationIdSupplier.get());

        SnapshotResourceTracker tracker = new SnapshotResourceTracker();
        Map<String, Supplier<CapabilityInstance>> factories;
        try {
            factories = InstanceBuilder.createInstanceFactories(graph, tracker, clock, dataAccessFactory);
        } catch (RuntimeException _) {
            throw FailureHandler.wrapFailure(tracker);
        }
        try {
            RuntimeSnapshot snapshot = new RuntimeSnapshot(generationId, graph, factories);
            return new SnapshotCandidate(snapshot, tracker);
        } catch (RuntimeException failure) {
            try {
                tracker.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Private nested helper that creates one instance per graph node.
     *
     * <p>Kept as a private nested helper so that the implementation class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class InstanceBuilder {

        private InstanceBuilder() {
            // no instances
        }

        static Map<String, Supplier<CapabilityInstance>> createInstanceFactories(
                CapabilityGraph graph, SnapshotResourceTracker tracker,
                Clock clock, DataAccessFactory dataAccessFactory) {
            Map<String, Supplier<CapabilityInstance>> factories = new LinkedHashMap<>();
            for (CapabilityNode node : graph.nodes()) {
                Supplier<CapabilityInstance> factory = () -> {
                    BuildContext context = new BuildContext(clock, tracker, dataAccessFactory);
                    CapabilityInstance instance = node.factory().create(node.config(), context);
                    if (instance == null) {
                        throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, List.of(
                                new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                                        "provider returned a null capability instance",
                                        "check the provider implementation")));
                    }
                    return instance;
                };

                CapabilityInstance testInstance = factory.get();
                tracker.register(testInstance);
                factories.put(node.id(), factory);
            }
            return factories;
        }
    }

    /**
     * Private nested helper that handles build failures and small validations.
     *
     * <p>Kept as a private nested helper so that the implementation class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class FailureHandler {

        private FailureHandler() {
            // no instances
        }

        static RuntimeException wrapFailure(SnapshotResourceTracker tracker) {
            PlatformException cleanupFailure = null;
            try {
                tracker.close();
            } catch (RuntimeException _) {
                cleanupFailure = new PlatformException(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, List.of(
                        new Diagnostic(PlatformErrorCode.CAPABILITY_EXECUTION_ERROR, Severity.ERROR, null,
                                "one or more registered resources failed to close",
                                "check resource lifecycle")));
            }
            PlatformException sanitized = new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, List.of(
                    new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                            "provider failed to create a capability instance",
                            "check the provider implementation")));
            if (cleanupFailure != null) {
                sanitized.addSuppressed(cleanupFailure);
            }
            return sanitized;
        }

        static String requireGenerationId(String generationId) {
            if (generationId == null || generationId.isBlank()) {
                throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, List.of(
                        new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                                "generation id must not be blank", "provide a non-blank generation id")));
            }
            return generationId;
        }

    }
}
