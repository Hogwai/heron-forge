package dev.hogwai.platform.runtime.snapshot;

import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.runtime.graph.CapabilityGraph;
import dev.hogwai.platform.runtime.graph.CapabilityNode;
import dev.hogwai.platform.runtime.graph.GraphCompiler;
import dev.hogwai.platform.runtime.provider.ProviderResolver;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import java.io.InputStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Builds a complete snapshot candidate from a configuration document.
 *
 * <p>The builder parses and validates the configuration with a
 * {@link SafeYamlParser}, compiles the application with a
 * {@link GraphCompiler} and {@link ProviderResolver}, then creates exactly one
 * {@link CapabilityInstance} per {@link CapabilityGraph} node. Each instance is
 * created with the raw safe configuration carried by the node and a
 * {@link BuildContext} that exposes only the application id, the snapshot id, a
 * {@link Clock} and the SPI {@link dev.hogwai.platform.spi.provider.ResourceTracker}.
 * Every created instance is registered with the runtime {@link ResourceTracker}
 * immediately so that a partial build can be torn down cleanly.
 *
 * <p>Providers are trusted: no additional dynamic resolution and no business
 * execution is performed. The result is an immutable package-private
 * {@link SnapshotCandidate} holding the {@link RuntimeSnapshot} and ownership
 * of the {@link ResourceTracker}.
 *
 * <p>If parsing, graph compilation, provider resolution, {@code create}, or
 * snapshot candidate assembly fails after partial creations, every
 * already-registered resource is closed in reverse order of registration, close
 * failures do not prevent the remaining closes, the primary error is preserved
 * and any close failures are attached as suppressed exceptions. Non-public
 * provider failures are converted into a stable {@link PlatformException}
 * without leaking details.
 */
public final class SnapshotBuilder {

    private final SafeYamlParser parser;
    private final ProviderResolver resolver;
    private final GraphCompiler compiler;
    private final Clock clock;
    private final Supplier<String> generationIdSupplier;

    /**
     * Creates a snapshot builder with the given collaborators.
     *
     * @param parser               the safe YAML parser
     * @param resolver             the provider resolver
     * @param compiler             the graph compiler
     * @param clock                the clock exposed to providers
     * @param generationIdSupplier supplies the non-blank generation id
     * @throws NullPointerException if any argument is {@code null}
     */
    SnapshotBuilder(SafeYamlParser parser, ProviderResolver resolver, GraphCompiler compiler,
                    Clock clock, Supplier<String> generationIdSupplier) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.generationIdSupplier = Objects.requireNonNull(generationIdSupplier, "generationIdSupplier must not be null");
    }

    /**
     * Builds a snapshot candidate from the given configuration document.
     *
     * @param input the configuration input stream
     * @return the immutable package-private candidate
     * @throws NullPointerException if {@code input} is {@code null}
     * @throws PlatformException    if parsing, graph compilation, provider
     *                              resolution or instance creation fails
     */
    SnapshotCandidate build(InputStream input) {
        Objects.requireNonNull(input, "input must not be null");
        ParsedApplication parsed = parser.parse(input);
        if (!parsed.isValid()) {
            throw new PlatformException(FailureHandler.firstErrorCode(parsed.diagnostics()), parsed.diagnostics());
        }
        ApplicationConfig application = parsed.application();
        CapabilityGraph graph = compiler.compile(application, resolver);
        String generationId = FailureHandler.requireGenerationId(generationIdSupplier.get());

        ResourceTracker tracker = new ResourceTracker();
        Map<String, CapabilityInstance> instances;
        try {
            instances = InstanceBuilder.createInstances(graph, application, generationId, tracker, clock);
        } catch (RuntimeException _) {
            throw FailureHandler.wrapFailure(tracker);
        }
        try {
            RuntimeSnapshot snapshot = new RuntimeSnapshot(generationId, graph, instances);
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
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class InstanceBuilder {

        private InstanceBuilder() {
            // no instances
        }

        static Map<String, CapabilityInstance> createInstances(CapabilityGraph graph, ApplicationConfig application,
                                                               String generationId, ResourceTracker tracker,
                                                               Clock clock) {
            Map<String, CapabilityInstance> instances = new LinkedHashMap<>();
            for (CapabilityNode node : graph.nodes()) {
                BuildContext context = new BuildContext(application.name(), generationId, clock, tracker);
                CapabilityInstance instance = node.factory().create(node.config(), context);
                if (instance == null) {
                    throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, List.of(
                            new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                                    "provider returned a null capability instance",
                                    "check the provider implementation")));
                }
                tracker.register(instance);
                instances.put(node.id(), instance);
            }
            return instances;
        }
    }

    /**
     * Private nested helper that handles build failures and small validations.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class FailureHandler {

        private FailureHandler() {
            // no instances
        }

        static RuntimeException wrapFailure(ResourceTracker tracker) {
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

        static PlatformErrorCode firstErrorCode(List<Diagnostic> diagnostics) {
            return diagnostics.stream()
                    .filter(d -> d.severity() == Severity.ERROR)
                    .findFirst()
                    .map(Diagnostic::code)
                    .orElse(PlatformErrorCode.CONFIG_PARSE_ERROR);
        }
    }
}
