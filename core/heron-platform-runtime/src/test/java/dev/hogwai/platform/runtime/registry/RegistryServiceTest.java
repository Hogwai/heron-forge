package dev.hogwai.platform.runtime.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link RegistryService}: sealing, idempotence and validation gating. */
class RegistryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private final InMemoryGenerationStore store = new InMemoryGenerationStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RegistryService service = new RegistryService(store, clock);

    @Test
    void registersASealedExperimentalRecordWithPredictableTimestamp() {
        RegistrationResult result = service.register(yaml("localhost"), "tester");
        GenerationRecord generationRecord = result.generationRecord();

        assertThat(generationRecord.applicationId()).isEqualTo("registry-demo");
        assertThat(generationRecord.generationId()).isEqualTo(sha256(yaml("localhost")));
        assertThat(generationRecord.configSha256()).isEqualTo(generationRecord.generationId());
        assertThat(generationRecord.rawYaml()).isEqualTo(yaml("localhost"));
        assertThat(generationRecord.status()).isEqualTo(GenerationStatus.EXPERIMENTAL);
        assertThat(generationRecord.createdAt()).isEqualTo(NOW);
        assertThat(generationRecord.createdBy()).isEqualTo("tester");
        assertThat(result.created()).isTrue();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void registeringIdenticalYamlReturnsTheExistingRecordUnchanged() {
        RegistrationResult first = service.register(yaml("localhost"), "tester");

        RegistrationResult second = service.register(yaml("localhost"), "someone-else");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.generationRecord()).isSameAs(first.generationRecord());
        assertThat(second.generationRecord().createdBy()).isEqualTo("tester");
        assertThat(second.generationRecord().createdAt()).isEqualTo(NOW);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void modifiedYamlCreatesADistinctGeneration() {
        GenerationRecord first = service.register(yaml("localhost"), "tester").generationRecord();

        GenerationRecord second = service.register(yaml("remote-host"), "tester").generationRecord();

        assertThat(second.generationId()).isNotEqualTo(first.generationId());
        assertThat(second.generationId()).isEqualTo(sha256(yaml("remote-host")));
        assertThat(second.createdAt()).isEqualTo(NOW);
        assertThat(service.register(yaml("remote-host"), "tester").created()).isFalse();
        assertThat(store.size()).isEqualTo(2);
        assertThat(store.find("registry-demo", second.generationId())).contains(second);
    }

    @Test
    void invalidYamlIsRejectedAndNothingIsPersisted() {
        assertThatThrownBy(() -> service.register("not: [valid", "tester"))
                .isInstanceOf(PlatformException.class)
                .satisfies(failure -> assertThat(((PlatformException) failure).code())
                        .isEqualTo(PlatformErrorCode.CONFIG_PARSE_ERROR));
        assertThat(store.size()).isZero();
    }

    @Test
    void unknownProviderIsRejectedByTheLoadingPathAndNothingIsPersisted() {
        String unknown = """
                apiVersion: heron.dev/v1
                application: registry-demo
                capabilities:
                  - id: source
                    provider:
                      id: does-not-exist
                      version: 1.0.0
                    config:
                      host: localhost
                """;

        assertThatThrownBy(() -> service.register(unknown, "tester"))
                .isInstanceOf(PlatformException.class)
                .satisfies(failure -> assertThat(((PlatformException) failure).code())
                        .isEqualTo(PlatformErrorCode.PROVIDER_NOT_FOUND));
        assertThat(store.size()).isZero();
    }

    @Test
    void nullArgumentsAreRejected() {
        var yaml = yaml("localhost");
        assertThatThrownBy(() -> service.register(null, "tester")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.register(yaml, null)).isInstanceOf(NullPointerException.class);
        assertThat(store.size()).isZero();
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
