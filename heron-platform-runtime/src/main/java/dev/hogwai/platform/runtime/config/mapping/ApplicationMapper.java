package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.List;
import java.util.Set;

/**
 * Package-private helper that maps the application root and {@code spec}
 * sections, keeping the public {@link ConfigMapper} within the project's
 * cyclomatic complexity budget.
 */
final class ApplicationMapper {

    private static final String API_VERSION = "platform.dev/v1alpha1";
    private static final String KIND = "Application";
    private static final Set<String> ROOT_FIELDS = Set.of("apiVersion", "kind", "metadata", "spec");
    private static final Set<String> SPEC_FIELDS = Set.of("capabilities");

    private ApplicationMapper() {
        // no instances
    }

    static ApplicationConfig map(JsonNode root, List<Diagnostic> diagnostics) {
        FieldChecks.rejectUnknownFields(root, ROOT_FIELDS, "", diagnostics);

        String apiVersion = FieldChecks.requiredString(root, "apiVersion", "", diagnostics);
        String kind = FieldChecks.requiredString(root, "kind", "", diagnostics);
        if (apiVersion != null && !API_VERSION.equals(apiVersion)) {
            diagnostics.add(Diagnostics.schemaError("/apiVersion",
                    "unsupported apiVersion", "use 'platform.dev/v1alpha1'"));
        }
        if (kind != null && !KIND.equals(kind)) {
            diagnostics.add(Diagnostics.schemaError("/kind",
                    "unsupported kind", "use 'Application'"));
        }

        String name = ConfigMapper.mapMetadata(root.get("metadata"), diagnostics);
        List<CapabilityConfig> capabilities = mapSpec(root.get("spec"), diagnostics);

        if (!diagnostics.isEmpty()) {
            return null;
        }
        return new ApplicationConfig(apiVersion, kind, name, capabilities);
    }

    private static List<CapabilityConfig> mapSpec(JsonNode spec, List<Diagnostic> diagnostics) {
        if (spec == null) {
            diagnostics.add(Diagnostics.schemaError("/spec",
                    "missing required member 'spec'", "add a spec mapping with capabilities"));
            return List.of();
        }
        if (!spec.isObject()) {
            diagnostics.add(Diagnostics.schemaError("/spec", "spec must be a mapping", "provide a mapping"));
            return List.of();
        }
        FieldChecks.rejectUnknownFields(spec, SPEC_FIELDS, "/spec", diagnostics);
        return CapabilityMapper.mapCapabilities(spec.get("capabilities"), diagnostics);
    }
}
