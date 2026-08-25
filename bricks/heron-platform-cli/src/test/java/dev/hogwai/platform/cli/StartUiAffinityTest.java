package dev.hogwai.platform.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Command-level tests for the advisory UI generation-affinity check performed
 * by {@code heron start --store} before booting: the boot always proceeds, the
 * check only prints.
 */
class StartUiAffinityTest {

    private static final String OTHER_GENERATION_ID =
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void warnsOnStderrWhenTheManifestWasBuiltForAnotherGeneration() throws IOException {
        Path storeRoot = temporaryDirectory.resolve("store");
        String generationId = registerStable(storeRoot);
        Path manifest = manifest("{\"applicationId\":\"cli-demo\",\"generationId\":\""
                + OTHER_GENERATION_ID + "\"}");

        RegistryCliTestSupport.Execution execution = start(storeRoot, manifest);

        assertThat(execution.status()).isZero();
        assertThat(execution.err()).contains("warning: activated generation "
                + generationId.substring(0, 12));
        assertThat(execution.err()).contains("was built for " + OTHER_GENERATION_ID.substring(0, 12));
        assertThat(execution.err()).contains("re-export widgets");
    }

    @Test
    void staysSilentWhenTheManifestMatchesTheActivatedGeneration() throws IOException {
        Path storeRoot = temporaryDirectory.resolve("store");
        String generationId = registerStable(storeRoot);
        Path manifest = manifest("{\"applicationId\":\"cli-demo\",\"generationId\":\""
                + generationId + "\"}");

        RegistryCliTestSupport.Execution execution = start(storeRoot, manifest);

        assertThat(execution.status()).isZero();
        assertThat(execution.err()).doesNotContain("re-export widgets");
        assertThat(execution.out()).doesNotContain("no UI manifest found");
    }

    @Test
    void printsAnInfoLineWhenNoManifestExists() throws IOException {
        Path storeRoot = temporaryDirectory.resolve("store");
        registerStable(storeRoot);
        Path manifest = temporaryDirectory.resolve("absent").resolve("widgets.json");

        RegistryCliTestSupport.Execution execution = start(storeRoot, manifest);

        assertThat(execution.status()).isZero();
        assertThat(execution.out())
                .contains("no UI manifest found — the dashboard was not part of this deployment");
        assertThat(execution.err()).doesNotContain("re-export widgets");
    }

    private RegistryCliTestSupport.Execution start(Path storeRoot, Path manifest) {
        StartCommand startCommand = new StartCommand();
        CountDownLatch shutdown = new CountDownLatch(1);
        startCommand.shutdownWaiter(shutdown::countDown);
        return RegistryCliTestSupport.execute(new CommandLine(startCommand),
                "--store", storeRoot.toString(), "--app", RegistryCliTestSupport.APPLICATION,
                "--port", "0", "--ui-manifest", manifest.toString());
    }

    private static String registerStable(Path storeRoot) throws IOException {
        Files.createDirectories(storeRoot);
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            String generationId = new dev.hogwai.platform.runtime.registry.RegistryService(store,
                    java.time.Clock.systemUTC()).register(RegistryCliTestSupport.yaml("localhost"), "tester")
                    .generationRecord().generationId();
            store.transition(RegistryCliTestSupport.APPLICATION, generationId, GenerationStatus.STABLE);
            return generationId;
        }
    }

    private Path manifest(String json) throws IOException {
        Path manifest = temporaryDirectory.resolve("generated").resolve("widgets.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, json, StandardCharsets.UTF_8);
        return manifest;
    }
}
