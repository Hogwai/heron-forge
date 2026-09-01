package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.runtime.config.Diagnostics;
import dev.hogwai.platform.runtime.config.EntrypointConfig;
import dev.hogwai.platform.runtime.config.WidgetConfig;
import dev.hogwai.platform.runtime.config.WorkerConfig;
import dev.hogwai.platform.spi.Diagnostic;

import java.util.List;
import java.util.Set;

/**
 * Package-private helper that maps the application root
 * sections, keeping the public {@link ConfigMapper} within the project's
 * cyclomatic complexity budget.
 */
final class ApplicationMapper {

    private static final String API_VERSION = "heron.dev/v1";
    private static final Set<String> ROOT_FIELDS =
            Set.of("apiVersion", "application", "capabilities", "endpoints", "widgets", "workers");

    private ApplicationMapper() {
        // no instances
    }

    static ApplicationConfig map(JsonNode root, List<Diagnostic> diagnostics) {
        FieldChecks.rejectUnknownFields(root, ROOT_FIELDS, "", diagnostics);

        String apiVersion = FieldChecks.requiredString(root, "apiVersion", "", diagnostics);
        String name = FieldChecks.requiredString(root, "application", "", diagnostics);
        if (apiVersion != null && !API_VERSION.equals(apiVersion)) {
            diagnostics.add(Diagnostics.schemaError("/apiVersion",
                    "unsupported apiVersion", "use 'heron.dev/v1'"));
        }

        List<CapabilityConfig> capabilities = CapabilityMapper.mapCapabilities(root.get("capabilities"), diagnostics);
        List<EntrypointConfig> entrypoints = EntrypointMapper.mapEntrypoints(
                root.get("endpoints"), "/endpoints", diagnostics);
        List<WidgetConfig> widgets = WidgetMapper.mapWidgets(root.get("widgets"), "/widgets", diagnostics);
        List<WorkerConfig> workers = WorkerMapper.mapWorkers(
                root.get("workers"), "/workers", diagnostics);

        if (!diagnostics.isEmpty()) {
            return null;
        }
        return new ApplicationConfig(apiVersion, name, capabilities, entrypoints, widgets, workers);
    }
}
