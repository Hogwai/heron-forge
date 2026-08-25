package dev.hogwai.platform.runtime.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import dev.hogwai.platform.runtime.execution.RuntimeApplication;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.ExecutionOutcome;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link GenerationActivator}: integrity gate, rebuild and execution. */
class GenerationActivatorTest {

    private static final Instant DEADLINE = Instant.parse("2099-01-01T00:00:00Z");

    private final InMemoryGenerationStore store = new InMemoryGenerationStore();
    private final RegistryService service = new RegistryService(store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private final RegistryTestSourceFactory factory = new RegistryTestSourceFactory();
    private final GenerationActivator activator =
            new GenerationActivator(Clock.systemUTC(), registry(factory), noDataAccess());

    @Test
    void activatesARegisteredGenerationAndInjectsItsIdIntoTheExecutionContext() {
        GenerationRecord generationRecord = service.register(yaml("localhost"), "tester").generationRecord();

        try (RuntimeApplication application = activator.activate(generationRecord)) {
            assertThat(application.entrypoints()).containsExactly(new EntrypointDescriptor("read", "/read"));
            ExecutionOutcome outcome = application.execute(
                    new InvocationRequest("read", "request-1", "correlation-1", DEADLINE, () -> false));
            assertThat(outcome.materialized()).isPresent();
            assertThat(factory.lastContext()).isNotNull();
            assertThat(factory.lastContext().snapshotId()).isEqualTo(generationRecord.generationId());
        }
    }

    @Test
    void tamperedYamlIsRejectedBeforeAnyCompilation() {
        GenerationRecord generationRecord = service.register(yaml("localhost"), "tester").generationRecord();
        String tampered = generationRecord.rawYaml().replace("localhost", "evil-host");
        GenerationRecord falsified = new GenerationRecord(generationRecord.applicationId(), generationRecord.generationId(),
                generationRecord.configSha256(), tampered, generationRecord.status(),
                generationRecord.createdAt(), generationRecord.createdBy());

        assertThatThrownBy(() -> activator.activate(falsified))
                .isInstanceOf(PlatformException.class)
                .satisfies(failure -> assertThat(((PlatformException) failure).code())
                        .isEqualTo(PlatformErrorCode.CONFIG_PARSE_ERROR));
        assertThat(factory.creations()).isZero();
    }

    @Test
    void invalidYamlIsRejectedEvenWhenSelfConsistent() {
        String invalid = "not: [valid";
        String digest = sha256(invalid);
        GenerationRecord generationRecord = new GenerationRecord("registry-demo", digest, digest, invalid,
                GenerationStatus.EXPERIMENTAL, Instant.EPOCH, "tester");

        assertThatThrownBy(() -> activator.activate(generationRecord))
                .isInstanceOf(PlatformException.class)
                .satisfies(failure -> assertThat(((PlatformException) failure).code())
                        .isEqualTo(PlatformErrorCode.CONFIG_PARSE_ERROR));
        assertThat(factory.creations()).isZero();
    }

    @Test
    void publicConstructorActivatesAMinimalConfigurationThroughServiceDiscovery() {
        GenerationRecord generationRecord = service.register(minimalYaml(), "tester").generationRecord();

        try (RuntimeApplication application = new GenerationActivator(Clock.systemUTC()).activate(generationRecord)) {
            assertThat(application.entrypoints()).isEmpty();
        }
    }

    @Test
    void nullRecordIsRejected() {
        assertThatThrownBy(() -> activator.activate(null)).isInstanceOf(NullPointerException.class);
    }

    private static RegistryTestProviderRegistry registry(RegistryTestSourceFactory factory) {
        RegistryTestProviderRegistry registry = new RegistryTestProviderRegistry();
        registry.add(factory);
        return registry;
    }

    private static DataAccessFactory noDataAccess() {
        return _ -> {
            throw new IllegalStateException("no data access expected in registry tests");
        };
    }

    private static String yaml(String host) {
        return """
                apiVersion: heron.dev/v1
                application: registry-demo
                capabilities:
                  - id: source
                    provider:
                      id: orders
                      version: 1.0.0
                    config:
                      host: %s
                endpoints:
                  - id: read
                    method: GET
                    path: /read
                    target: source
                """.formatted(host);
    }

    private static String minimalYaml() {
        return """
                apiVersion: heron.dev/v1
                application: registry-minimal
                capabilities: []
                """;
    }

    private static String sha256(String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("the SHA-256 message digest algorithm is not available", failure);
        }
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
