package dev.hogwai.platform.runtime.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.hogwai.platform.runtime.config.WidgetConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;

/**
 * Validates widget declarations against the declared application surface.
 *
 * <p>Widgets reference HTTP entrypoints by id, so unlike capability inputs they
 * are checked against the declared entrypoint ids rather than graph node ids.
 * The check runs on both boot paths (direct load and registry activation).
 */
public final class WidgetValidator {

    private WidgetValidator() {
        // no instances
    }

    /**
     * Validates that every widget target references a declared entrypoint.
     *
     * @param entrypointIds the declared entrypoint ids
     * @param widgets       the mapped widget declarations
     * @return the diagnostics, empty when all targets are valid
     */
    public static List<Diagnostic> validate(Set<String> entrypointIds, List<WidgetConfig> widgets) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < widgets.size(); i++) {
            if (!entrypointIds.contains(widgets.get(i).target())) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                        "/widgets/" + i + "/target", "widget target does not exist",
                        "reference an existing endpoint id"));
            }
        }
        return diagnostics;
    }
}
