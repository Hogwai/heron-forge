package dev.hogwai.platform.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.registry.RegistryService;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class WidgetsExportCommandTest {

    private static final String APPLICATION = "widget-demo";

    private static final String YAML = """
            apiVersion: heron.dev/v1
            application: widget-demo
            capabilities:
              - id: orders
                provider:
                  id: cli-orders
                  version: 1.0.0
                config:
                  host: localhost
            endpoints:
              - id: orders-endpoint
                method: GET
                path: /orders
                target: orders
            widgets:
              - id: orders-kpi
                type: kpi
                title: Orders
                target: orders-endpoint
            """;

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void exportsTheSealedGenerationFromTheStore() throws IOException {
        Path store = tempDir.resolve("store");
        String storedGenerationId = register(store, YAML);
        Path output = tempDir.resolve("out").resolve("widgets.json");

        CommandLine commandLine = new CommandLine(new WidgetsExportCommand());
        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(commandLine,
                "--store", store.toString(), "--app", APPLICATION, "--output", output.toString());

        assertThat(execution.status()).isZero();
        Map<String, Object> manifest = new ObjectMapper().readValue(
                Files.readString(output, StandardCharsets.UTF_8), Map.class);
        assertThat(manifest)
                .containsEntry("applicationId", APPLICATION)
                .containsEntry("generationId", storedGenerationId);
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) manifest.get("widgets");
        assertThat(widgets).hasSize(1);
        assertThat(widgets.getFirst())
                .containsEntry("id", "orders-kpi")
                .containsEntry("type", "kpi")
                .containsEntry("title", "Orders")
                .containsEntry("path", "/orders");
        assertThat(execution.out()).contains(storedGenerationId).contains(store.toString());
    }

    @Test
    void failsWhenTheStoredYamlIsTampered() throws IOException {
        Path store = tempDir.resolve("store");
        String generationId = register(store, YAML);
        tamper(store, generationId);
        Path output = tempDir.resolve("widgets.json");

        CommandLine commandLine = new CommandLine(new WidgetsExportCommand());
        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(commandLine,
                "--store", store.toString(), "--app", APPLICATION, "--output", output.toString());

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("integrity check");
        assertThat(output).doesNotExist();
    }

    @Test
    void refusesARetiredGenerationWithThePolicyMessage() throws IOException {
        Path store = tempDir.resolve("store");
        String generationId = register(store, YAML);
        try (FileGenerationStore generationStore = new FileGenerationStore(store)) {
            generationStore.transition(APPLICATION, generationId, GenerationStatus.STABLE);
            generationStore.transition(APPLICATION, generationId, GenerationStatus.DEPRECATED);
            generationStore.transition(APPLICATION, generationId, GenerationStatus.RETIRED);
        }
        Path output = tempDir.resolve("widgets.json");

        CommandLine commandLine = new CommandLine(new WidgetsExportCommand());
        RegistryCliTestSupport.Execution execution = RegistryCliTestSupport.execute(commandLine,
                "--store", store.toString(), "--app", APPLICATION,
                "--generation", generationId, "--output", output.toString());

        assertThat(execution.status()).isEqualTo(1);
        assertThat(execution.err()).contains("RETIRED");
        assertThat(output).doesNotExist();
    }

    private static String register(Path storeRoot, String rawYaml) throws IOException {
        Files.createDirectories(storeRoot);
        try (FileGenerationStore generationStore = new FileGenerationStore(storeRoot)) {
            String generationId = new RegistryService(generationStore, Clock.systemUTC())
                    .register(rawYaml, "tester").generationRecord().generationId();
            generationStore.transition(APPLICATION, generationId, GenerationStatus.STABLE);
            return generationId;
        }
    }

    private static void tamper(Path storeRoot, String generationId) {
        try (FileGenerationStore generationStore = new FileGenerationStore(storeRoot)) {
            GenerationRecord original = generationStore.find(APPLICATION, generationId).orElseThrow();
            String tamperedYaml = original.rawYaml().replace("title: Orders", "title: Tampered");
            generationStore.save(new GenerationRecord(original.applicationId(), original.generationId(),
                    original.configSha256(), tamperedYaml, original.status(), original.createdAt(),
                    original.createdBy()));
        }
    }
}
