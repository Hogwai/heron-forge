package dev.hogwai.platform.runtime.registry;

import java.util.ArrayList;
import java.util.List;

import dev.hogwai.platform.runtime.compile.CapabilityGraph;
import dev.hogwai.platform.runtime.config.EntrypointConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;

/**
 * Validates declared entrypoints against the compiled graph, mirroring the
 * loading-path rule so activations reject dangling targets identically.
 */
final class RegistryEntrypointValidator {

    private RegistryEntrypointValidator() {
        // no instances
    }

    /**
     * Returns a diagnostic for every entrypoint whose target is not a graph node.
     *
     * @param graph       the compiled capability graph
     * @param entrypoints the declared entrypoints
     * @return the diagnostics; empty when every target exists
     */
    static List<Diagnostic> validate(CapabilityGraph graph, List<EntrypointConfig> entrypoints) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < entrypoints.size(); i++) {
            if (!graph.nodeIds().contains(entrypoints.get(i).target())) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                        "/endpoints/%d/target".formatted(i), "endpoint target does not exist",
                        "reference an existing capability"));
            }
        }
        return diagnostics;
    }
}
