package dev.hogwai.platform.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.load.WidgetExports;
import dev.hogwai.platform.runtime.load.WidgetExports.ResolvedWidget;
import dev.hogwai.platform.runtime.registry.GenerationDigest;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Picocli subcommand that exports the widget declarations of a SEALED
 * generation as a JSON manifest.
 *
 * <p>The export reads the store, never a raw file: the manifest is stamped
 * with the store's generation id after an integrity check, so an unregistered
 * draft can never look official. Environment placeholders in the stored YAML
 * resolve at export time, exactly like activation.
 */
@Command(name = "export",
        description = "Export the validated widget declarations of a sealed generation as a JSON manifest "
                + "(environment placeholders resolve at export time)")
public final class WidgetsExportCommand implements Callable<Integer> {

    @Option(names = "--store", defaultValue = StartCommand.DEFAULT_STORE,
            description = "generation store root directory (default: ./" + StartCommand.DEFAULT_STORE + ")")
    private Path store;

    @Option(names = "--app", required = true, description = "application id in the generation store")
    private String applicationId;

    @Option(names = "--generation", description = "explicit generation id (defaults to the latest STABLE; "
            + "allows DEPRECATED with a warning; refuses RETIRED)")
    private String generationId;

    @Option(names = "--output", required = true, description = "target JSON manifest file to write")
    private Path output;

    @Spec
    private CommandSpec commandSpec;

    /**
     * Creates the picocli widgets export command.
     */
    public WidgetsExportCommand() {
        // populated by picocli
    }

    @Override
    public Integer call() throws IOException {
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            GenerationSelection.Selected selected =
                    GenerationSelection.select(generationStore.history(applicationId), generationId);
            GenerationRecord generationRecord = selected.generationRecord();
            if (selected.warning() != null) {
                commandSpec.commandLine().getOut().println(selected.warning());
            }
            String digest = GenerationDigest.sha256Hex(generationRecord.rawYaml());
            if (!digest.equals(generationRecord.generationId())) {
                commandSpec.commandLine().getErr().println("heron: generation '%s' failed its integrity check; "
                        .formatted(generationRecord.generationId())
                        + "the stored YAML does not match the sealed generation id");
                return 1;
            }
            WidgetExports.Export export = WidgetExports.export(
                    new ByteArrayInputStream(generationRecord.rawYaml().getBytes(StandardCharsets.UTF_8)));
            write(manifestJson(export.applicationName(), generationRecord.generationId(), export.widgets()));
            commandSpec.commandLine().getOut()
                    .println("exported %d widget(s) from application '%s' generation %s (store %s) to %s"
                            .formatted(export.widgets().size(), applicationId, generationRecord.generationId(), store, output));
            return 0;
        } catch (IllegalArgumentException | PlatformException | UncheckedIOException failure) {
            commandSpec.commandLine().getErr().println("heron: " + failure.getMessage());
            return 1;
        }
    }

    private static String manifestJson(String applicationName, String generationId, List<ResolvedWidget> widgets) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("applicationId", applicationName);
        manifest.put("generationId", generationId);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ResolvedWidget widget : widgets) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", widget.id());
            entry.put("type", widget.type());
            entry.put("title", widget.title());
            entry.put("path", widget.path());
            entries.add(entry);
        }
        manifest.put("widgets", entries);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            return mapper.writeValueAsString(manifest);
        } catch (IOException failure) {
            throw new IllegalStateException("the widget manifest could not be serialized", failure);
        }
    }

    private void write(String content) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, content, StandardCharsets.UTF_8);
    }
}
