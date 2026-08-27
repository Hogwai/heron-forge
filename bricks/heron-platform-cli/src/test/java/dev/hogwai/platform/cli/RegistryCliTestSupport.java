package dev.hogwai.platform.cli;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

import dev.hogwai.platform.registry.FileGenerationStore;
import dev.hogwai.platform.runtime.registry.RegistryService;
import dev.hogwai.platform.spi.registry.GenerationRecord;
import dev.hogwai.platform.spi.registry.GenerationStatus;
import picocli.CommandLine;

/**
 * Shared fixtures for the registry CLI tests.
 */
final class RegistryCliTestSupport {

    static final String APPLICATION = "cli-demo";

    private RegistryCliTestSupport() {
        // no instances
    }

    static String yaml(String host) {
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
                    path: /read
                    target: source
                """.formatted(APPLICATION, host);
    }

    static String register(Path storeRoot, String rawYaml) {
        try (FileGenerationStore generationStore = new FileGenerationStore(storeRoot)) {
            return new RegistryService(generationStore, Clock.systemUTC()).register(rawYaml, "tester")
                    .generationRecord().generationId();
        }
    }

    static void save(FileGenerationStore store, String generationId, GenerationStatus status,
                     Instant createdAt, String rawYaml) {
        String digest = sha256(rawYaml);
        store.save(new GenerationRecord(APPLICATION, generationId, digest, rawYaml, status,
                createdAt, "tester"));
    }

    static Execution execute(CommandLine commandLine, String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        commandLine.setOut(new java.io.PrintWriter(out, true, StandardCharsets.UTF_8));
        commandLine.setErr(new java.io.PrintWriter(err, true, StandardCharsets.UTF_8));
        int status = commandLine.execute(arguments);
        return new Execution(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    static String sha256(String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("the SHA-256 message digest algorithm is not available", failure);
        }
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Captured outcome of a picocli execution.
     */
    record Execution(int status, String out, String err) {
    }
}
