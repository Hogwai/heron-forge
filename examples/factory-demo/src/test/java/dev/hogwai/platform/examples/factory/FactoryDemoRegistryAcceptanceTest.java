package dev.hogwai.platform.examples.factory;

import dev.hogwai.platform.host.helidon.HelidonHostAdapter;
import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.execution.RuntimeApplication;
import dev.hogwai.platform.runtime.registry.GenerationActivator;
import dev.hogwai.platform.runtime.registry.RegistrationResult;
import dev.hogwai.platform.runtime.registry.RegistryService;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import dev.hogwai.platform.spi.registry.GenerationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance check for the full generation-registry consumer path of the
 * factory-demo: a real {@link FileGenerationStore} in a temporary directory,
 * a real {@link RegistryService} discovering the demo providers through the
 * {@code ServiceLoader}, then promotion to {@code STABLE}, activation through
 * {@link GenerationActivator} and HTTP probes against the booted Helidon host.
 *
 * <p>Like {@link FactoryDemoAcceptanceTest}, this test assumes the Compose
 * PostgreSQL fixture is up and the {@code HERON_DB_*} environment variables
 * are exported; it is skipped otherwise.
 */
class FactoryDemoRegistryAcceptanceTest {

    private static final String CONFIGURATION_RESOURCE = "/supply-chain.yaml";
    private static final String APPLICATION_ID = "supply-chain-demo";
    private static final String CREATED_BY = "acceptance-test";
    private static final HostConfiguration HOST_CONFIGURATION =
            new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(5));

    @TempDir
    private Path storeRoot;

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
    void registersPromotesActivatesAndServesOverHttp() throws Exception {
        String yaml = configuration();
        String expectedGenerationId = sha256Hex(yaml);
        try (GenerationStore store = new FileGenerationStore(storeRoot)) {
            RegistryService registry = new RegistryService(store, Clock.systemUTC());

            RegistrationResult registration = registry.register(yaml, CREATED_BY);
            assertThat(registration.created()).isTrue();
            GenerationRecord generationRecord = registration.generationRecord();
            assertThat(generationRecord.applicationId()).isEqualTo(APPLICATION_ID);
            assertThat(generationRecord.generationId()).isEqualTo(expectedGenerationId);
            assertThat(generationRecord.status()).isEqualTo(GenerationStatus.EXPERIMENTAL);
            assertThat(generationRecord.createdBy()).isEqualTo(CREATED_BY);

            RegistrationResult replay = registry.register(yaml, CREATED_BY);
            assertThat(replay.created()).isFalse();
            assertThat(replay.generationRecord()).isEqualTo(generationRecord);

            assertThat(store.transition(APPLICATION_ID, expectedGenerationId, GenerationStatus.STABLE))
                    .isTrue();
            GenerationRecord stable = store.find(APPLICATION_ID, expectedGenerationId).orElseThrow();
            assertThat(stable.status()).isEqualTo(GenerationStatus.STABLE);

            activateServeAndProbe(stable);
        }
    }

    /**
     * Activates the sealed generationRecord through the public ServiceLoader-based
     * constructor, boots the same Helidon wiring as the shell acceptance test
     * on an ephemeral port and probes the health and exception endpoints.
     * Application and host are closed even when a probe fails.
     */
    private void activateServeAndProbe(GenerationRecord stable) throws Exception {
        try (RuntimeApplication application = new GenerationActivator(Clock.systemUTC()).activate(stable);
             HelidonHostAdapter adapter = new HelidonHostAdapter();
             HttpClient client = HttpClient.newHttpClient()) {
            adapter.start(application, HOST_CONFIGURATION);
            URI baseUri = URI.create("http://127.0.0.1:" + adapter.port());

            assertThat(get(client, baseUri.resolve("/health/live")).statusCode()).isEqualTo(200);
            assertThat(get(client, baseUri.resolve("/health/ready")).statusCode()).isEqualTo(200);

            HttpResponse<String> exceptions = get(client, baseUri.resolve("/exceptions"));
            assertThat(exceptions.statusCode()).isEqualTo(200);
            assertThat(exceptions.headers().firstValue("content-type"))
                    .hasValue("application/json; charset=utf-8");
            assertThat(exceptions.body()).contains(
                    "\"rowCount\":3",
                    "\"exceptionType\":\"LATE_DELIVERY\"",
                    "\"exceptionType\":\"INSUFFICIENT_QUANTITY\"",
                    "\"exceptionType\":\"PRIORITY_RISK\"");
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String configuration() {
        try (InputStream input = FactoryDemoRegistryAcceptanceTest.class.getResourceAsStream(CONFIGURATION_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("factory-demo configuration resource is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("factory-demo configuration could not be read", failure);
        }
    }

    /**
     * Mirrors the sealing identity: SHA-256 hex digest of the UTF-8 YAML bytes.
     */
    private static String sha256Hex(String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("the SHA-256 message digest algorithm is not available", failure);
        }
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
