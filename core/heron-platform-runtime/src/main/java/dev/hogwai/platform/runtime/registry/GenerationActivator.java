package dev.hogwai.platform.runtime.registry;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.runtime.compile.provider.ServiceLoaderProviderRegistry;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.runtime.execution.RuntimeApplication;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.registry.GenerationRecord;

/**
 * Rebuilds a live application from a sealed {@link GenerationRecord}.
 *
 * <p>Activation is deterministic: the raw YAML of the generationRecord is reparsed by
 * the same safe path used at registration, then compiled with the generationRecord's
 * generation id injected via the {@code SnapshotBuilder} generation id
 * supplier, so every {@code ExecutionContext} built from the resulting
 * snapshot carries the sealed identity.
 *
 * <p>The integrity of the seal is checked before any compilation work: the
 * SHA-256 digest of {@link GenerationRecord#rawYaml()} must equal
 * {@link GenerationRecord#generationId()}. A mismatch means the stored
 * definition was tampered with and activation fails with a
 * {@link PlatformException} without creating any provider instance.
 */
public final class GenerationActivator {

    private final RuntimeApplicationFactory factory;

    /**
     * Creates an activator that discovers providers on the trusted classpath.
     *
     * @param clock the clock exposed to providers and to the invoker
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public GenerationActivator(Clock clock) {
        this(clock, new ServiceLoaderProviderRegistry(), RegistryDataAccess.discover());
    }

    /**
     * Creates an activator with explicit collaborators, primarily for
     * composition and tests. This constructor is package-private on purpose:
     * production code always discovers providers through
     * {@link ServiceLoaderProviderRegistry}.
     *
     * @param clock             the clock exposed to providers and to the invoker
     * @param registry          the provider registry to compile against
     * @param dataAccessFactory the data access factory exposed to providers
     * @throws NullPointerException if any argument is {@code null}
     */
    GenerationActivator(Clock clock, ProviderRegistry registry, DataAccessFactory dataAccessFactory) {
        Objects.requireNonNull(clock, "clock must not be null");
        this.factory = new RuntimeApplicationFactory(clock, registry, dataAccessFactory);
    }

    /**
     * Rebuilds the application described by the given generationRecord.
     *
     * @param generationRecord the sealed generation generationRecord to activate
     * @return the live application owning its provider resources; close it when done
     * @throws NullPointerException if {@code generationRecord} is {@code null}
     * @throws PlatformException    with {@code CONFIG_PARSE_ERROR} when the
     *                              integrity check fails (the raw YAML no longer
     *                              hashes to the recorded generation id) or when
     *                              the YAML fails parsing or schema validation,
     *                              in both cases before any compilation
     * @throws PlatformException    with the underlying compilation or reference
     *                              error code when instance creation or entrypoint
     *                              validation fails; already-created resources are
     *                              closed before the failure surfaces
     */
    public RuntimeApplication activate(GenerationRecord generationRecord) {
        Objects.requireNonNull(generationRecord, "generationRecord must not be null");
        String computed = GenerationDigest.sha256Hex(generationRecord.rawYaml());
        if (!computed.equals(generationRecord.generationId())) {
            throw sealedContentMismatch();
        }

        ParsedApplication parsed = new SafeYamlParser().parse(RegistrySupport.bytes(generationRecord.rawYaml()));
        if (!parsed.isValid()) {
            throw new PlatformException(RegistrySupport.firstErrorCode(parsed.diagnostics()), parsed.diagnostics());
        }
        return factory.build(parsed, generationRecord);
    }

    private static PlatformException sealedContentMismatch() {
        return new PlatformException(PlatformErrorCode.CONFIG_PARSE_ERROR, List.of(
                new Diagnostic(PlatformErrorCode.CONFIG_PARSE_ERROR, Severity.ERROR, null,
                        "generation integrity check failed: the raw configuration does not hash "
                                + "to the recorded generation id",
                        "re-register the application to seal a fresh generation")));
    }
}
