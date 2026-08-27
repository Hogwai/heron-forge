package dev.hogwai.platform.registry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import dev.hogwai.platform.spi.registry.GenerationStore;

/**
 * File-based {@link GenerationStore} persisting sealed generation definitions
 * on the local filesystem.
 *
 * <p>Disk layout: each record lives in its own directory
 * {@code <root>/<applicationId>/<generationId>/} holding two files:
 * {@code config.yaml}, the raw YAML written <em>verbatim</em> (environment
 * placeholders such as {@code ${VAR}} are never resolved before storage), and
 * {@code record.json}, the self-contained serialized
 * {@link GenerationRecord}. {@code record.json} is the source of truth for
 * reads; {@code config.yaml} is the canonical configuration artifact.
 *
 * <p>Writes are atomic per file: content goes to a temporary file in the same
 * directory, then lands at its final name with a non-atomic fallback when the filesystem does not support atomic moves.
 * Saving a known {@code (applicationId, generationId)} pair replaces the
 * stored entry cleanly (idempotent upsert).
 *
 * <p>Corrupted or unreadable {@code record.json} entries are ignored
 * silently: {@link #find} reports them as absent and {@link #history} skips
 * them, so a damaged entry never crashes readers.
 *
 * <p>The default constructor resolves the store root from the environment:
 * the {@code HERON_REGISTRY_DIR} variable when set and non-blank, otherwise
 * {@code ./registry} relative to the current working directory. The root is
 * created lazily on first write.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
@HeronService(value = GenerationStore.class, id = "registry.file")
public final class FileGenerationStore implements GenerationStore {

    /**
     * Explicit lifecycle order; never derived from {@code ordinal()}.
     */
    private static final List<GenerationStatus> LIFECYCLE = List.of(
            GenerationStatus.EXPERIMENTAL,
            GenerationStatus.STABLE,
            GenerationStatus.DEPRECATED,
            GenerationStatus.RETIRED);

    private static final String CONFIG_FILE = "config.yaml";
    private static final String RECORD_FILE = "record.json";
    private static final String DEFAULT_ROOT = "registry";
    private static final String ROOT_ENV_VARIABLE = "HERON_REGISTRY_DIR";

    /**
     * Shared mapper; {@code ObjectMapper} is thread-safe for read and write.
     */
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String GENERATION_ID = "generationId";
    public static final String APPLICATION_ID = "applicationId";

    private final Path root;

    /**
     * Creates a store rooted at the supplied directory.
     *
     * @param root directory under which generations are archived
     */
    public FileGenerationStore(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    /**
     * Creates a store whose root comes from the environment.
     *
     * <p>The root is the value of the {@code HERON_REGISTRY_DIR} environment
     * variable when set and non-blank, otherwise {@code ./registry} relative
     * to the current working directory.
     */
    public FileGenerationStore() {
        this(defaultRoot());
    }

    private static Path defaultRoot() {
        String configured = System.getenv(ROOT_ENV_VARIABLE);
        return configured == null || configured.isBlank()
                ? Path.of(DEFAULT_ROOT)
                : Path.of(configured);
    }

    @Override
    public void save(GenerationRecord generationRecord) {
        Objects.requireNonNull(generationRecord, "record must not be null");
        Path directory = generationDirectory(generationRecord.applicationId(), generationRecord.generationId());
        try {
            writeAtomically(directory.resolve(CONFIG_FILE),
                    generationRecord.rawYaml().getBytes(StandardCharsets.UTF_8));
            writeAtomically(directory.resolve(RECORD_FILE), serialize(generationRecord));
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to persist generation '%s' of application '%s'"
                    .formatted(generationRecord.generationId(), generationRecord.applicationId()), failure);
        }
    }

    @Override
    public Optional<GenerationRecord> find(String applicationId, String generationId) {
        Path file = generationDirectory(applicationId, generationId).resolve(RECORD_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(read(file));
        } catch (IOException | RuntimeException _) {
            // Corrupted or unreadable entries are treated as absent, never fatal.
            return Optional.empty();
        }
    }

    @Override
    public List<GenerationRecord> history(String applicationId) {
        Path applicationDirectory = root.resolve(requireSegment(applicationId, APPLICATION_ID));
        if (!Files.isDirectory(applicationDirectory)) {
            return List.of();
        }
        List<GenerationRecord> records = new ArrayList<>();
        try (Stream<Path> entries = Files.list(applicationDirectory)) {
            for (Path directory : entries.filter(Files::isDirectory).toList()) {
                readQuietly(directory.resolve(RECORD_FILE)).ifPresent(records::add);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "failed to list generations of application '" + applicationId + "'", failure);
        }
        records.sort(Comparator.comparing(GenerationRecord::createdAt, Comparator.reverseOrder())
                .thenComparing(GenerationRecord::generationId));
        return List.copyOf(records);
    }

    @Override
    public boolean transition(String applicationId, String generationId, GenerationStatus target) {
        Objects.requireNonNull(target, "target must not be null");
        Optional<GenerationRecord> current = find(applicationId, generationId);
        if (current.isEmpty()) {
            return false;
        }
        GenerationRecord generationRecord = current.get();
        if (LIFECYCLE.indexOf(target) <= LIFECYCLE.indexOf(generationRecord.status())) {
            // Backward transitions, same-status no-ops and RETIRED (terminal)
            // are all rejected by this single strictly-forward check.
            return false;
        }
        save(new GenerationRecord(generationRecord.applicationId(), generationRecord.generationId(),
                generationRecord.configSha256(), generationRecord.rawYaml(), target,
                generationRecord.createdAt(), generationRecord.createdBy()));
        return true;
    }

    /**
     * Releases resources owned by this store.
     *
     * <p>This implementation holds no pooled or open resources, so closing is
     * a safe no-op and idempotent by construction.
     */
    @Override
    public void close() {
        // No resources to release; kept idempotent per the GenerationStore contract.
    }

    private Optional<GenerationRecord> readQuietly(Path file) {
        try {
            return Optional.of(read(file));
        } catch (IOException | RuntimeException _) {
            // Corrupted entries are skipped silently by history().
            return Optional.empty();
        }
    }

    private static GenerationRecord read(Path file) throws IOException {
        return deserialize(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Serializes a record into a flat JSON object of string fields
     * ({@code createdAt} as ISO-8601, {@code status} as the enum name).
     */
    private static byte[] serialize(GenerationRecord generationRecord) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(APPLICATION_ID, generationRecord.applicationId());
        fields.put(GENERATION_ID, generationRecord.generationId());
        fields.put("configSha256", generationRecord.configSha256());
        fields.put("rawYaml", generationRecord.rawYaml());
        fields.put("status", generationRecord.status().name());
        fields.put("createdAt", generationRecord.createdAt().toString());
        fields.put("createdBy", generationRecord.createdBy());
        return JSON.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses a flat JSON object produced by {@link #serialize}.
     *
     * @throws IOException              when the text is not well-formed JSON or a
     *                                  value is not a string; callers translate this
     *                                  into silent skipping of corrupted entries
     * @throws IllegalArgumentException when a field is missing or unparseable;
     *                                  handled like any other corruption signal
     */
    private static GenerationRecord deserialize(String json) throws IOException {
        Map<String, String> fields = JSON.readValue(json, new TypeReference<>() {
        });
        return new GenerationRecord(
                required(fields, APPLICATION_ID),
                required(fields, GENERATION_ID),
                required(fields, "configSha256"),
                required(fields, "rawYaml"),
                GenerationStatus.valueOf(required(fields, "status")),
                Instant.parse(required(fields, "createdAt")),
                required(fields, "createdBy"));
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing field '" + key + "' in record JSON");
        }
        return value;
    }

    private Path generationDirectory(String applicationId, String generationId) {
        return root.resolve(requireSegment(applicationId, APPLICATION_ID))
                .resolve(requireSegment(generationId, GENERATION_ID));
    }

    private static String requireSegment(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank() || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(name + " must be a plain path segment: '" + value + "'");
        }
        return value;
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path directory = target.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, content, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
