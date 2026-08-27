package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * YAML fixtures shared by snapshot builder tests.
 */
final class SnapshotBuilderTestYaml {

    private SnapshotBuilderTestYaml() {
    }

    static ApplicationConfig application(String name, TestCap... caps) {
        ParsedApplication parsed = new SafeYamlParser().parse(new ByteArrayInputStream(
                yaml(name, caps).getBytes(StandardCharsets.UTF_8)));
        if (!parsed.isValid()) {
            throw new AssertionError("test application must be valid: " + parsed.diagnostics());
        }
        return parsed.application();
    }

    static String yaml(String name, TestCap... caps) {
        StringBuilder builder = new StringBuilder();
        builder.append("apiVersion: heron.dev/v1\n");
        builder.append("application: ").append(name).append("\n");
        builder.append("capabilities:\n");
        for (TestCap capability : caps) {
            builder.append("  - id: ").append(capability.id()).append("\n");
            builder.append("    provider:\n");
            builder.append("      id: ").append(capability.providerId()).append("\n");
            builder.append("      version: ").append(capability.version()).append("\n");
            if (!capability.inputs().isEmpty()) {
                builder.append("    inputs:\n");
                for (TestInput input : capability.inputs()) {
                    builder.append("      ").append(input.inputPort()).append(":\n");
                    builder.append("        capability: ").append(input.capability()).append("\n");
                    builder.append("        port: ").append(input.port()).append("\n");
                }
            }
            builder.append("    config:\n");
            builder.append("      host: localhost\n");
        }
        return builder.toString();
    }

    record TestInput(String inputPort, String capability, String port) {
    }

    record TestCap(String id, String providerId, String version, List<TestInput> inputs) {
        TestCap(String id, String providerId, String version) {
            this(id, providerId, version, List.of());
        }
    }
}
