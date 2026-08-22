package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformVersionsTest {

    @Test
    void loadsResolvedVersionsFromTheFilteredResource() {
        assertThat(PlatformVersions.value("platform.version"))
                .isNotBlank()
                .doesNotContain("@");
        assertThat(PlatformVersions.templateModel())
                .containsEntry("platformVersion", PlatformVersions.value("platform.version"))
                .containsEntry("junitJupiterVersion", PlatformVersions.value("junit.jupiter.version"))
                .containsEntry("assertjVersion", PlatformVersions.value("assertj.version"))
                .containsEntry("javaToolchainVersion", PlatformVersions.value("java.toolchain.version"))
                .containsEntry("kotlinPluginVersion", PlatformVersions.value("kotlin.plugin.version"))
                .containsEntry("platformSpiVersion", PlatformVersions.value("platform.spi.version"));
    }
}
