package dev.hogwai.platform.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/** Writes a complete generated project without overwriting existing content. */
final class ProjectWriter {

    private ProjectWriter() {
        // utility class
    }

    static void writeAll(Path target, Map<String, String> files) throws IOException {
        ensureTargetIsWritable(target);
        Files.createDirectories(target);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = target.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static void ensureTargetIsWritable(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalStateException("target is not a directory: " + target);
        }
        try (Stream<Path> entries = Files.list(target)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalStateException("target directory already exists and is not empty: " + target);
            }
        }
    }
}
