package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAppCommandTest {

    @TempDir
    Path tempDirectory;

    @Test
    void scaffoldsTheApplicationStarter() throws Exception {
        CreateAppCommand command = new CreateAppCommand(tempDirectory);
        command.name = "demo-app";

        assertThat(command.call()).isZero();

        Path project = tempDirectory.resolve("demo-app");
        assertThat(project.resolve("settings.gradle.kts")).exists();
        assertThat(project.resolve("build.gradle.kts")).exists();
        assertThat(project.resolve("src/main/resources/application.yaml")).exists();
        assertThat(project.resolve("src/main/java/demoapp/HelloProviderFactory.java")).exists();
        assertThat(Files.readString(project.resolve("src/main/java/demoapp/HelloProviderFactory.java")))
                .contains("@HeronService");
        assertThat(project.resolve("src/main/resources/META-INF/services/"
                + "dev.hogwai.platform.spi.provider.ProviderFactory")).doesNotExist();
        assertThat(Files.readString(project.resolve("src/main/resources/application.yaml")))
                .contains("application: demo-app")
                .contains("id: demoapp.hello");
    }

    @Test
    void honorsAnExplicitPackage() throws Exception {
        CreateAppCommand command = new CreateAppCommand(tempDirectory);
        command.name = "demo-app";
        command.packageName = "com.acme.heron";

        assertThat(command.call()).isZero();

        Path source = tempDirectory.resolve("demo-app/src/main/java/com/acme/heron/HelloProviderFactory.java");
        assertThat(source).exists();
        assertThat(Files.readString(source)).contains("package com.acme.heron;");
    }

    @Test
    void refusesToOverwriteNonEmptyDirectory() throws Exception {
        Path project = tempDirectory.resolve("taken");
        Files.createDirectories(project);
        Files.writeString(project.resolve("keep.txt"), "keep");

        CreateAppCommand command = new CreateAppCommand(tempDirectory);
        command.name = "taken";

        assertThat(command.call()).isOne();
        assertThat(project.resolve("keep.txt")).exists();
        assertThat(project.resolve("build.gradle.kts")).doesNotExist();
    }
}
