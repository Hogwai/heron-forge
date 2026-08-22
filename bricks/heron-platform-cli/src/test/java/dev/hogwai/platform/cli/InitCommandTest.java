package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void scaffoldsCompleteProjectSkeleton() throws Exception {
        InitCommand command = new InitCommand(tempDir);
        command.name = "demo-app";

        int status = command.call();

        assertThat(status).isZero();
        Path project = tempDir.resolve("demo-app");
        assertThat(project.resolve("settings.gradle.kts")).exists();
        assertThat(project.resolve("build.gradle.kts")).exists();
        assertThat(project.resolve("src/main/resources/application.yaml")).exists();
        assertThat(project.resolve("src/main/java/demoapp/HelloProviderFactory.java")).exists();
        assertThat(Files.readString(project.resolve("src/main/java/demoapp/HelloProviderFactory.java")))
                .contains("@HeronService");
        assertThat(project.resolve("src/main/resources/META-INF/services/"
                + "dev.hogwai.platform.spi.provider.ProviderFactory")).doesNotExist();
        assertThat(Files.readString(project.resolve("settings.gradle.kts")))
                .contains("rootProject.name = \"demo-app\"");
        assertThat(Files.readString(project.resolve("build.gradle.kts")))
                .contains("dev.hogwai.platform:heron-platform-runtime:" + InitCommand.PLATFORM_VERSION)
                .contains("mainClass.set(\"dev.hogwai.platform.cli.HeronLauncher\")");
        assertThat(Files.readString(project.resolve("src/main/resources/application.yaml")))
                .contains("apiVersion: heron.dev/v1")
                .contains("id: demoapp.hello")
                .contains("path: /hello");
        assertThat(Files.readString(project.resolve("src/main/java/demoapp/HelloProviderFactory.java")))
                .contains("package demoapp;")
                .contains("new ProviderId(\"demoapp.hello\")");
        assertThat(Files.readString(project.resolve("src/main/java/demoapp/HelloProviderFactory.java")))
                .contains("demoapp.hello");
    }

    @Test
    void honorsExplicitPackageOption() throws Exception {
        InitCommand command = new InitCommand(tempDir);
        command.name = "demo-app";
        command.packageName = "com.acme.heron";

        int status = command.call();

        assertThat(status).isZero();
        Path provider = tempDir.resolve("demo-app/src/main/java/com/acme/heron/HelloProviderFactory.java");
        assertThat(provider).exists();
        assertThat(Files.readString(provider)).contains("package com.acme.heron;");
        assertThat(Files.readString(tempDir.resolve("demo-app/src/main/resources/application.yaml")))
                .contains("id: com.acme.heron.hello");
    }

    @Test
    void refusesToOverwriteNonEmptyDirectory() throws Exception {
        Path existing = tempDir.resolve("taken");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("keep.txt"), "x");

        InitCommand command = new InitCommand(tempDir);
        command.name = "taken";

        int status = command.call();

        assertThat(status).isOne();
        assertThat(existing.resolve("keep.txt")).exists();
        assertThat(existing.resolve("build.gradle.kts")).doesNotExist();
    }

    @Test
    void rejectsInvalidProjectNames() {
        assertThat(withName("../evil").call()).isOne();
        assertThat(withName("a/b").call()).isOne();
        assertThat(withName("").call()).isOne();
    }

    private InitCommand withName(String value) {
        InitCommand command = new InitCommand(tempDir);
        command.name = value;
        return command;
    }
}