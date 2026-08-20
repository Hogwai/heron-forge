package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.List;
import java.util.Set;

/**
 * Maps the {@code provider} section of a capability declaration.
 *
 * <p>Package-private helper that keeps the public
 * {@link dev.hogwai.platform.runtime.config.ConfigValidator} within the
 * project's cyclomatic complexity budget.
 */
final class ProviderMapper {

    private static final Set<String> PROVIDER_FIELDS = Set.of("id", "version");

    private ProviderMapper() {
        // no instances
    }

    /**
     * Maps a {@code provider} mapping.
     *
     * @param provider    the provider node
     * @param path        the structural path
     * @param diagnostics the diagnostics collector
     * @return the validated provider reference, or {@code null} if invalid
     */
    static ProviderRef mapProvider(JsonNode provider, String path, List<Diagnostic> diagnostics) {
        if (provider == null) {
            diagnostics.add(Diagnostics.schemaError(path,
                    "missing required member 'provider'", "add a provider mapping"));
            return null;
        }
        if (!provider.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path, "provider must be a mapping", "provide a mapping"));
            return null;
        }
        FieldChecks.rejectUnknownFields(provider, PROVIDER_FIELDS, path, diagnostics);
        String id = FieldChecks.requiredString(provider, "id", path, diagnostics);
        String version = FieldChecks.requiredString(provider, "version", path, diagnostics);
        if (version != null && !FieldChecks.isCanonicalVersion(version)) {
            diagnostics.add(Diagnostics.schemaError(path + "/version",
                    "provider version must be canonical major.minor.patch", "use a version such as '1.2.3'"));
        }
        if (id == null || version == null) {
            return null;
        }
        return new ProviderRef(id, version);
    }
}
