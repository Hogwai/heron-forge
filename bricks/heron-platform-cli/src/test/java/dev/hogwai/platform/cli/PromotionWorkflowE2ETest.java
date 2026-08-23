package dev.hogwai.platform.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end promotion workflow over the real picocli commands: register v1,
 * promote it, register a modified v2, diff both, promote v2, roll back to v1
 * and finally check the start-selection policy.
 */
class PromotionWorkflowE2ETest {

    private static final String APPLICATION = "promo-demo";

    @TempDir
    Path temporaryDirectory;

    @Test
    void fullPromotionWorkflowFromRegisterToRollback() throws Exception {
        Path storeRoot = temporaryDirectory.resolve("store");

        // register v1 and verify the sealed EXPERIMENTAL generation.
        String v1 = promoYaml("localhost", "/read");
        Path v1Configuration = configurationFile("v1.yaml", v1);
        RegistryCliTestSupport.Execution registration = execute(new CommandLine(new RegisterCommand()),
                "--config", v1Configuration.toString(), "--store", storeRoot.toString(),
                "--created-by", "tester");
        String g1 = RegistryCliTestSupport.sha256(v1);
        assertThat(registration.status()).isZero();
        assertThat(registration.out()).contains("registered application '" + APPLICATION + "'")
                .contains(g1)
                .contains("EXPERIMENTAL");
        assertThat(execute(new CommandLine(new GenerationsListCommand()),
                "--store", storeRoot.toString(), "--app", APPLICATION).out())
                .contains(g1)
                .contains("EXPERIMENTAL");

        // promote v1 to STABLE.
        RegistryCliTestSupport.Execution promotion = execute(new CommandLine(new GenerationsMarkCommand()),
                "--store", storeRoot.toString(), "--app", APPLICATION,
                "--generation", g1, "--status", "stable");
        assertThat(promotion.status()).isZero();
        assertThat(promotion.out()).contains("moved from EXPERIMENTAL to STABLE");

        // register a modified v2: different config value and endpoint path hence a different content-derived generation id.
        Thread.sleep(50);
        String v2 = promoYaml("remote", "/read-v2");
        Path v2Configuration = configurationFile("v2.yaml", v2);
        RegistryCliTestSupport.Execution secondRegistration =
                execute(new CommandLine(new RegisterCommand()),
                        "--config", v2Configuration.toString(), "--store", storeRoot.toString(),
                        "--created-by", "tester");
        String g2 = RegistryCliTestSupport.sha256(v2);
        assertThat(secondRegistration.status()).isZero();
        assertThat(g2).isNotEqualTo(g1);
        assertThat(secondRegistration.out()).contains(g2);

        // diff v1 -> v2 describes exactly the changed config key and endpoint path.
        RegistryCliTestSupport.Execution difference = execute(new CommandLine(new GenerationsDiffCommand()),
                "--store", storeRoot.toString(), "--app", APPLICATION,
                "--from", g1, "--to", g2);
        assertThat(difference.status()).isZero();
        assertThat(difference.out())
                .contains("~ capability.config 'source'")
                .contains("'host': localhost -> remote")
                .contains("~ endpoint 'read'")
                .contains("path /read -> /read-v2")
                .doesNotContain("no differences");

        // promote v2: two STABLE generations now, v2 the most recent.
        RegistryCliTestSupport.Execution secondPromotion =
                execute(new CommandLine(new GenerationsMarkCommand()),
                        "--store", storeRoot.toString(), "--app", APPLICATION,
                        "--generation", g2, "--status", "stable");
        assertThat(secondPromotion.status()).isZero();
        assertThat(secondPromotion.out()).contains("moved from EXPERIMENTAL to STABLE");
        assertThat(execute(new CommandLine(new GenerationsListCommand()),
                "--store", storeRoot.toString(), "--app", APPLICATION).out())
                .containsSubsequence(g2, g1);

        // rollback resolves the previous STABLE generation (v1) and prints the exact activation command referencing it.
        RegistryCliTestSupport.Execution rollback = execute(new CommandLine(new RollbackCommand()),
                "--store", storeRoot.toString(), "--app", APPLICATION);
        assertThat(rollback.status()).isZero();
        assertThat(rollback.out()).contains(g1)
                .contains("heron start --store %s --app %s --generation %s"
                        .formatted(storeRoot, APPLICATION, g1));

        // selection policy: default points at the newest STABLE (v2) while explicitly selecting v1 stays permitted.
        try (FileGenerationStore store = new FileGenerationStore(storeRoot)) {
            List<GenerationRecord> history = store.history(APPLICATION);
            assertThat(GenerationSelection.select(history, null).generationRecord().generationId()).isEqualTo(g2);
            GenerationSelection.Selected explicit = GenerationSelection.select(history, g1);
            assertThat(explicit.generationRecord().generationId()).isEqualTo(g1);
            assertThat(explicit.warning()).isNull();
        }
    }

    private static RegistryCliTestSupport.Execution execute(CommandLine commandLine, String... arguments) {
        return RegistryCliTestSupport.execute(commandLine, arguments);
    }

    private Path configurationFile(String fileName, String rawYaml) throws IOException {
        Path configuration = temporaryDirectory.resolve(fileName);
        Files.writeString(configuration, rawYaml);
        return configuration;
    }

    private static String promoYaml(String host, String path) {
        return """
                apiVersion: heron.dev/v1
                application: %s
                capabilities:
                  - id: source
                    provider:
                      id: cli-orders
                      version: 1.0.0
                    config:
                      host: %s
                endpoints:
                  - id: read
                    method: GET
                    path: %s
                    target: source
                """.formatted(APPLICATION, host, path);
    }
}
