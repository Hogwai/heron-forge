package dev.hogwai.platform.spi.registry;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of one sealed application generation held by a
 * {@link GenerationStore}.
 *
 * <p>The {@code rawYaml} is the raw, unresolved configuration exactly as
 * submitted: environment placeholders ({@code ${VAR}}) are never persisted in
 * resolved form. The {@code configSha256} seals the canonical configuration
 * content and makes identity provable: two records with identical content
 * share the same {@code generationId}.
 *
 * @param applicationId identifier of the application this generation belongs to
 * @param generationId  content-derived identifier of the generation (unique per application)
 * @param configSha256  SHA-256 hex digest of the canonical configuration content
 * @param rawYaml       the raw, unresolved YAML configuration of the generation
 * @param status        current lifecycle status within the monotone generation lifecycle
 * @param createdAt     instant at which the generation was registered
 * @param createdBy     free-form attribution of who or what registered the generation
 */
public record GenerationRecord(String applicationId, String generationId, String configSha256,
                               String rawYaml, GenerationStatus status, Instant createdAt,
                               String createdBy) {

    /** Validates the record components. */
    public GenerationRecord {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        if (applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId must not be blank");
        }
        Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) {
            throw new IllegalArgumentException("generationId must not be blank");
        }
        Objects.requireNonNull(configSha256, "configSha256 must not be null");
        if (configSha256.isBlank()) {
            throw new IllegalArgumentException("configSha256 must not be blank");
        }
        Objects.requireNonNull(rawYaml, "rawYaml must not be null");
        if (rawYaml.isBlank()) {
            throw new IllegalArgumentException("rawYaml must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
    }
}
