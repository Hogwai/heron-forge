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
        assertThat(project.resolve("src/main/java/com/acme/postgres/PostgresDataAccessFactory.java")).exists();
        Path service = project.resolve("src/main/resources/META-INF/services/"
                + "dev.hogwai.platform.spi.data.access.DataAccessFactory");
        assertThat(service).exists();
        assertThat(Files.readString(service)).isEqualTo("com.acme.postgres.PostgresDataAccessFactory\n");
    }

    @Test
    void scaffoldsAHostBrick() throws Exception {
        CreateBrickCommand command = command("helidon", "com.acme.helidon", "host");

        assertThat(command.call()).isZero();

        Path project = tempDirectory.resolve("helidon");
        assertThat(project.resolve("src/main/java/com/acme/helidon/HelidonHostAdapter.java")).exists();
        Path service = project.resolve("src/main/resources/META-INF/services/"
                + "dev.hogwai.platform.spi.host.HostAdapter");
        assertThat(service).exists();
        assertThat(Files.readString(service)).isEqualTo("com.acme.helidon.HelidonHostAdapter\n");
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
