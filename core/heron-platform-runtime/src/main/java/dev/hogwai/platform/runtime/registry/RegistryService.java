package dev.hogwai.platform.runtime.registry;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.runtime.load.ApplicationLoader;
import dev.hogwai.platform.runtime.load.ValidationReport;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import dev.hogwai.platform.spi.registry.GenerationStore;

/**
 * Registers sealed application generations into a {@link GenerationStore}.
 *
 * <p>Registration never executes the configuration and never persists an unvalidated document:
 * the raw YAML is first validated through the standard loading path {@link SafeYamlParser} for the secure lexical gate and
 * {@link ApplicationLoader#validate(java.io.InputStream)} for graph-level checks.
 * Any validation failure surfaces as the {@link PlatformException} of the existing loading path and nothing is written to the store.
 *
 * <p>The generation identity is content-derived: the {@code generationId} is
 * the full SHA-256 hexadecimal digest of the UTF-8 bytes of the raw YAML, so
 * two registrations of identical content are provably idempotent. When the
 * store already holds the generationRecord for the pair
 * {@code (applicationId, generationId)}, the existing generationRecord is returned
 * unchanged.
 */
public final class RegistryService {

    private final GenerationStore store;
    private final Clock clock;

    /**
     * Creates a registry service.
     *
     * @param store the persistent generation store
     * @param clock the clock used to timestamp new generations
     * @throws NullPointerException if any argument is {@code null}
     */
    public RegistryService(GenerationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Validates, seals and stores one application generation.
     *
     * <p>The raw YAML is validated first through the existing loading path;
     * on failure the corresponding {@link PlatformException} is thrown and the
     * store is left untouched. On success the application id is extracted from
     * the parsed configuration ({@code application:} field) and the generation
     * id is computed as the SHA-256 digest of the raw YAML bytes. Registration
     * is idempotent: an already-known pair returns the stored generationRecord unchanged,
     * flagged with {@code created = false}.
     *
     * @param rawYaml   the raw, unresolved YAML configuration of the application
     * @param createdBy free-form attribution of who or what registers the generation
     * @return the result carrying the stored generationRecord either
     * the newly persisted {@link GenerationStatus#EXPERIMENTAL} generationRecord with {@code created = true} or
     * the pre-existing generationRecord for identical content with {@code created = false}
     * @throws NullPointerException if any argument is {@code null}
     * @throws PlatformException    if the YAML fails parsing, schema or graph
     *                              validation; nothing is persisted in that case
     */
    public RegistrationResult register(String rawYaml, String createdBy) {
        Objects.requireNonNull(rawYaml, "rawYaml must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");

        ParsedApplication parsed = new SafeYamlParser().parse(RegistrySupport.bytes(rawYaml));
        if (!parsed.isValid()) {
            throw new PlatformException(RegistrySupport.firstErrorCode(parsed.diagnostics()), parsed.diagnostics());
        }
        ValidationReport report = ApplicationLoader.validate(RegistrySupport.bytes(rawYaml));
        if (!report.valid()) {
            throw new PlatformException(RegistrySupport.firstErrorCode(report.diagnostics()), report.diagnostics());
        }

        String applicationId = parsed.application().name();
        String generationId = GenerationDigest.sha256Hex(rawYaml);
        Optional<GenerationRecord> existing = store.find(applicationId, generationId);
        if (existing.isPresent()) {
            return new RegistrationResult(existing.get(), false);
        }
        GenerationRecord generationRecord = new GenerationRecord(applicationId, generationId, generationId,
                rawYaml, GenerationStatus.EXPERIMENTAL, clock.instant(), createdBy);
        store.save(generationRecord);
        return new RegistrationResult(generationRecord, true);
    }
}
