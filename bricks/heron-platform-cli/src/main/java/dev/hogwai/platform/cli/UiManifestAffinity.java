package dev.hogwai.platform.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Compares the activated generation id with the generation id baked into the
 * exported UI widget manifest. The check is purely advisory: it prints a
 * warning on mismatch and an info line when no manifest exists, but it never
 * blocks or fails the boot.
 */
final class UiManifestAffinity {

    private static final int SHORT_ID_LENGTH = 12;

    private final Path uiManifest;
    private final CommandSpec commandSpec;

    /**
     * Creates an affinity check against one exported manifest.
     *
     * @param uiManifest  the path of the exported UI widget manifest
     * @param commandSpec the picocli spec used for user-facing output
     */
    UiManifestAffinity(Path uiManifest, CommandSpec commandSpec) {
        this.uiManifest = Objects.requireNonNull(uiManifest, "uiManifest must not be null");
        this.commandSpec = Objects.requireNonNull(commandSpec, "commandSpec must not be null");
    }

    /**
     * Compares the activated generation id with the id of the exported UI
     * manifest. Advisory only: any read or parse failure is swallowed so the
     * boot is never blocked.
     *
     * @param activatedGenerationId the id of the generation being activated
     */
    void report(String activatedGenerationId) {
        if (!Files.exists(uiManifest)) {
            commandSpec.commandLine().getOut()
                    .println("no UI manifest found — the dashboard was not part of this deployment");
            return;
        }
        manifestGenerationId().ifPresent(manifestGenerationId -> {
            if (!manifestGenerationId.equals(activatedGenerationId)) {
                String warning = ("warning: activated generation %s but the UI manifest was built for %s "
                        + "— re-export widgets")
                        .formatted(shorten(activatedGenerationId), shorten(manifestGenerationId));
                commandSpec.commandLine().getErr().println(warning);
            }
        });
    }

    /**
     * Reads the {@code generationId} field of the manifest leniently: a
     * malformed file or a missing/non-text field yields an empty optional.
     */
    private Optional<String> manifestGenerationId() {
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(uiManifest));
            JsonNode field = root == null ? null : root.get("generationId");
            if (field != null && field.isTextual()) {
                return Optional.of(field.asText());
            }
            return Optional.empty();
        } catch (IOException | RuntimeException _) {
            return Optional.empty();
        }
    }

    private static String shorten(String generationId) {
        return generationId.length() > SHORT_ID_LENGTH
                ? generationId.substring(0, SHORT_ID_LENGTH)
                : generationId;
    }
}
