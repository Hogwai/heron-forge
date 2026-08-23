package dev.hogwai.platform.runtime.registry;

import java.time.Instant;

import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural comparison of two generation records through
 * {@link GenerationDiff}, with YAML fixtures covering every change kind.
 */
class GenerationDiffTest {

    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void identicalYieldsNoDifferences() {
        GenerationDiff.DiffResult result = GenerationDiff.diff(generateRecord(yaml("localhost", "/read")),
                generateRecord(yaml("localhost", "/read")));

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.entries()).isEmpty();
        assertThat(result.render()).isEqualTo("no differences");
    }

    @Test
    void detectsAddedAndRemovedCapabilities() {
        String before = """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: kept
                    provider:
                      id: cli-orders
                      version: 1.0.0
                  - id: dropped
                    provider:
                      id: cli-orders
                      version: 1.0.0
                endpoints: []
                """;
        String after = """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: kept
                    provider:
                      id: cli-orders
                      version: 1.0.0
                  - id: fresh
                    provider:
                      id: cli-orders
                      version: 2.0.0
                endpoints: []
                """;

        GenerationDiff.DiffResult result = GenerationDiff.diff(generateRecord(before), generateRecord(after));

        assertThat(result.entries()).containsExactly(
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.REMOVED, "capability", "dropped",
                        "provider 'cli-orders:1.0.0'"),
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.ADDED, "capability", "fresh",
                        "provider 'cli-orders:2.0.0'"));
    }

    @Test
    void detectsModifiedProviderConfigAndInputs() {
        String before = """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: source
                    provider:
                      id: cli-orders
                      version: 1.0.0
                    config:
                      host: localhost
                      port: 8080
                  - id: sink
                    provider:
                      id: cli-orders
                      version: 1.0.0
                    inputs:
                      in:
                        capability: source
                        port: out
                endpoints: []
                """;
        String after = """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: source
                    provider:
                      id: cli-orders
                      version: 1.1.0
                    config:
                      host: remote
                  - id: sink
                    provider:
                      id: cli-orders
                      version: 1.0.0
                    inputs:
                      in:
                        capability: other-source
                        port: out-2
                endpoints: []
                """;

        GenerationDiff.DiffResult result = GenerationDiff.diff(generateRecord(before), generateRecord(after));

        assertThat(result.entries()).containsExactly(
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.MODIFIED, "capability.input",
                        "sink", "'in': source.out -> other-source.out-2"),
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.MODIFIED, "capability", "source",
                        "provider 'cli-orders:1.0.0' -> 'cli-orders:1.1.0'"),
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.MODIFIED, "capability.config",
                        "source", "'host': localhost -> remote"),
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.REMOVED, "capability.config",
                        "source", "'port' (was 8080)"));
        assertThat(result.render()).contains("~ capability 'source'");
    }

    @Test
    void detectsAddedRemovedAndModifiedEndpoints() {
        String before = """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: source
                    provider:
                      id: cli-orders
                      version: 1.0.0
                endpoints:
                  - id: read
                    method: GET
                    path: /read
                    target: source
                  - id: stale
                    method: GET
                    path: /stale
                    target: source
                """;
        String after = """
                apiVersion: heron.dev/v1
                application: cli-demo
                capabilities:
                  - id: source
                    provider:
                      id: cli-orders
                      version: 1.0.0
                endpoints:
                  - id: read
                    method: GET
                    path: /read-v2
                    target: other-source
                  - id: write
                    method: GET
                    path: /write
                    target: source
                """;

        GenerationDiff.DiffResult result = GenerationDiff.diff(generateRecord(before), generateRecord(after));

        assertThat(result.entries()).containsExactly(
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.MODIFIED, "endpoint", "read",
                        "path /read -> /read-v2; target source -> other-source"),
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.REMOVED, "endpoint", "stale",
                        "GET /stale -> source"),
                new GenerationDiff.DiffEntry(GenerationDiff.ChangeType.ADDED, "endpoint", "write",
                        "GET /write -> source"));
    }

    @Test
    void invalidYamlFailsCleanlyWithPlatformException() {
        GenerationRecord invalid = generateRecord("this is: [not: valid yaml");
        var yaml = yaml("localhost", "/read");
        var generationRecord = generateRecord(yaml);
        assertThatThrownBy(() -> GenerationDiff.diff(invalid, generationRecord))
                .isInstanceOf(PlatformException.class);
    }

    private static GenerationRecord generateRecord(String rawYaml) {
        String digest = GenerationDigest.sha256Hex(rawYaml);
        return new GenerationRecord("cli-demo", digest, digest, rawYaml,
                GenerationStatus.EXPERIMENTAL, T0, "tester");
    }

    private static String yaml(String host, String path) {
        return """
                apiVersion: heron.dev/v1
                application: cli-demo
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
                """.formatted(host, path);
    }
}
