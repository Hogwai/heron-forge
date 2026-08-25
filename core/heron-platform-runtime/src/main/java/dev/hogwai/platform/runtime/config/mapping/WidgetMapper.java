package dev.hogwai.platform.runtime.config.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.Diagnostics;
import dev.hogwai.platform.runtime.config.WidgetConfig;
import dev.hogwai.platform.spi.Diagnostic;

/**
 * Maps the optional {@code widgets} list.
 * Package-private so the public mapping surface remains {@link ConfigMapper}.
 */
final class WidgetMapper {

    static final String PATH_SEPARATOR = "/";

    private WidgetMapper() {
        // no instances
    }

    /**
     * Maps widgets in YAML order.
     *
     * @param widgetsNode the widgets node, or null when omitted
     * @param path        the structural path of the widgets list
     * @param diagnostics the diagnostics collector
     * @return the mapped widgets
     */
    static List<WidgetConfig> mapWidgets(JsonNode widgetsNode, String path, List<Diagnostic> diagnostics) {
        if (widgetsNode == null) {
            return List.of();
        }
        if (!widgetsNode.isArray()) {
            diagnostics.add(Diagnostics.schemaError(path, "widgets must be a list", "provide a list"));
            return List.of();
        }

        List<WidgetConfig> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < widgetsNode.size(); i++) {
            String widgetPath = path + PATH_SEPARATOR + i;
            WidgetConfig mapped = WidgetDeclarationMapper.map(widgetsNode.get(i), widgetPath, diagnostics);
            if (mapped == null) {
                continue;
            }
            if (!seenIds.add(mapped.id())) {
                diagnostics.add(Diagnostics.schemaError(widgetPath + PATH_SEPARATOR + "id",
                        "duplicate widget id", "use a unique id"));
            }
            result.add(mapped);
        }
        return List.copyOf(result);
    }
}

/** Maps and validates one widget declaration. */
final class WidgetDeclarationMapper {

    private static final Set<String> WIDGET_FIELDS = Set.of("id", "type", "title", "target");

    private WidgetDeclarationMapper() {
        // no instances
    }

    static WidgetConfig map(JsonNode widget, String path, List<Diagnostic> diagnostics) {
        if (widget == null || !widget.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path, "widget must be a mapping", "provide a mapping"));
            return null;
        }
        FieldChecks.rejectUnknownFields(widget, WIDGET_FIELDS, path, diagnostics);

        String id = EntrypointFieldValidator.requiredString(widget, "id", path, diagnostics);
        String type = EntrypointFieldValidator.requiredString(widget, "type", path, diagnostics);
        String title = EntrypointFieldValidator.requiredString(widget, "title", path, diagnostics);
        String target = EntrypointFieldValidator.requiredString(widget, "target", path, diagnostics);
        if (id == null || type == null || title == null || target == null) {
            return null;
        }
        if (!WidgetConfig.ALLOWED_TYPES.contains(type)) {
            diagnostics.add(Diagnostics.schemaError(path + WidgetMapper.PATH_SEPARATOR + "type",
                    "unknown widget type", "use one of kpi, table, chart"));
            return null;
        }
        return new WidgetConfig(id, type, title, target);
    }
}
