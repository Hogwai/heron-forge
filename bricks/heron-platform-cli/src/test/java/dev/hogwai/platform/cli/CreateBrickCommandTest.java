package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CreateBrickCommandTest {

    @TempDir
    Path tempDirectory;

    @Test
    void scaffoldsADataBrick() throws Exception {
        CreateBrickCommand command = command("postgres", "com.acme.postgres", "data");

        assertThat(command.call()).isZero();

        Path project = tempDirectory.resolve("postgres");
        assertThat(project.resolve("settings.gradle.kts")).exists();
        assertThat(project.resolve("build.gradle.kts")).exists();
        Path source = project.resolve("src/main/java/com/acme/postgres/PostgresDataAccessFactory.java");
        assertThat(source).exists();
        assertThat(Files.readString(source))
                .contains("@HeronService(value = DataAccessFactory.class")
                .contains("id = \"data.postgres\"");
        assertThat(Files.readString(project.resolve("build.gradle.kts")))
                .contains("annotationProcessor(\"dev.hogwai.platform:heron-platform-processor:");
        assertThat(project.resolve("src/main/resources/META-INF/services")).doesNotExist();
    }

    @Test
    void scaffoldsAHostBrick() throws Exception {
        CreateBrickCommand command = command("helidon", "com.acme.helidon", "host");

        assertThat(command.call()).isZero();

        Path project = tempDirectory.resolve("helidon");
        Path source = project.resolve("src/main/java/com/acme/helidon/HelidonHostAdapter.java");
        assertThat(source).exists();
        assertThat(Files.readString(source))
                .contains("@HeronService(value = HostAdapter.class")
                .contains("id = \"host.helidon\"");
        assertThat(project.resolve("src/main/resources/META-INF/services")).doesNotExist();
    }

    @Test
    void rejectsAnUnknownBrickTypeBeforeWriting() {
        CreateBrickCommand command = command("postgres", "com.acme.postgres", "unknown");

        assertThat(command.call()).isOne();
        assertThat(tempDirectory.resolve("postgres")).doesNotExist();
    }

    private CreateBrickCommand command(String name, String packageName, String type) {
        CreateBrickCommand command = new CreateBrickCommand(tempDirectory);
        command.name = name;
        command.packageName = packageName;
        command.type = type;
        return command;
    }
}
