package dev.hogwai.platform.cli;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Picocli converter accepting only readable regular files. */
final class ReadablePathConverter implements ITypeConverter<Path> {

    /** Creates the converter. */
    ReadablePathConverter() {
        // no instances needed; picocli instantiates it reflectively
    }

    @Override
    public Path convert(String value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            Path path = Path.of(value);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new TypeConversionException("configuration file is not readable");
            }
            return path;
        } catch (InvalidPathException | SecurityException _) {
            throw new TypeConversionException("configuration file path is invalid");
        }
    }
}
