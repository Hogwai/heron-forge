package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectWriterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesNestedFilesAsUtf8() throws Exception {
        ProjectWriter.writeAll(tempDirectory.resolve("project"),
                Map.of("src/main/Foo.java", "é"));

        assertThat(Files.readString(tempDirectory.resolve("project/src/main/Foo.java")))
                .isEqualTo("é");
    }

    @Test
    void allowsAnEmptyExistingTarget() throws Exception {
        Path target = tempDirectory.resolve("project");
        Files.createDirectories(target);

        ProjectWriter.writeAll(target, Map.of("file.txt", "content"));

        assertThat(Files.readString(target.resolve("file.txt"))).isEqualTo("content");
    }

    @Test
    void refusesToWriteIntoANonEmptyTarget() throws Exception {
        Path target = tempDirectory.resolve("project");
        Files.createDirectories(target);
        Files.writeString(target.resolve("keep.txt"), "keep");

        var map = Map.of("new.txt", "new");
        assertThatThrownBy(() -> ProjectWriter.writeAll(target, map))
                .isInstanceOf(IllegalStateException.class);
        assertThat(target.resolve("new.txt")).doesNotExist();
        assertThat(Files.readString(target.resolve("keep.txt"))).isEqualTo("keep");
    }
}
