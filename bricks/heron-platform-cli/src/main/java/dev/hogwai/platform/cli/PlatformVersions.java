package dev.hogwai.platform.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/** Loads dependency versions embedded in the CLI distribution. */
final class PlatformVersions {

    private static final Properties VALUES = load();

    private PlatformVersions() {
        // utility class
    }

    static String value(String key) {
        String value = VALUES.getProperty(key);
        if (value == null || value.isBlank() || value.contains("@")) {
            throw new IllegalStateException("unresolved platform version: " + key);
        }
        return value;
    }

    static Map<String, String> templateModel() {
        return Map.of(
                "platformVersion", value("platform.version"),
                "slf4jVersion", value("slf4j.version"),
                "junitJupiterVersion", value("junit.jupiter.version"),
                "assertjVersion", value("assertj.version"),
                "javaToolchainVersion", value("java.toolchain.version"),
                "gradleVersion", value("gradle.version"));
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = PlatformVersions.class.getResourceAsStream("/heron-platform.properties")) {
            if (input == null) {
                throw new IllegalStateException("heron-platform.properties not found on the classpath");
            }
            properties.load(input);
            return properties;
        } catch (IOException failure) {
            throw new IllegalStateException("failed to load heron-platform.properties", failure);
        }
    }
}
