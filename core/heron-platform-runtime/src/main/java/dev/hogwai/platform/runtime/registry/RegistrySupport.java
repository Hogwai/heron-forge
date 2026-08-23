package dev.hogwai.platform.runtime.registry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;

/**
 * Shared helpers of the registry layer: UTF-8 stream wrapping and stable
 * error-code selection from validation diagnostics.
 */
final class RegistrySupport {

    private RegistrySupport() {
        // no instances
    }

    /**
     * Wraps raw YAML content into a fresh bounded stream for the parsing path.
     *
     * @param rawYaml the raw YAML content
     * @return a stream over the UTF-8 bytes of the content
     */
    static ByteArrayInputStream bytes(String rawYaml) {
        return new ByteArrayInputStream(rawYaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the code of the first error diagnostic, falling back to
     * {@link PlatformErrorCode#CONFIG_PARSE_ERROR} when none carries a code.
     *
     * @param diagnostics the diagnostics to inspect
     * @return the first error code
     */
    static PlatformErrorCode firstErrorCode(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                .map(Diagnostic::code)
                .findFirst()
                .orElse(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }
}
