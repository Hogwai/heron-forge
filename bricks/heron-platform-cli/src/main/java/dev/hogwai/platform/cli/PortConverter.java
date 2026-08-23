package dev.hogwai.platform.cli;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Picocli converter validating HTTP bind ports (0..65535). */
final class PortConverter implements ITypeConverter<Integer> {

    /** Creates the converter; picocli instantiates it reflectively. */
    PortConverter() {
        // no instances needed
    }

    @Override
    public Integer convert(String value) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new TypeConversionException("port must be 0 through 65535");
        }
        if (parsed < 0 || parsed > 65535) {
            throw new TypeConversionException("port must be 0 through 65535");
        }
        return parsed;
    }
}
