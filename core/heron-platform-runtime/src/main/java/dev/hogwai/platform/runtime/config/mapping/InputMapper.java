package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.Diagnostics;
import dev.hogwai.platform.runtime.config.InputBindingConfig;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the {@code inputs} mapping and each input binding of a capability.
 *
 * <p>The {@code inputs} mapping keys are the target input port names on the
 * current capability; each value is a binding with exactly {@code capability}
 * and {@code port}. The target input port name is preserved explicitly on the
 * resulting {@link InputBindingConfig}. Package-private helper that keeps the
 * public {@link dev.hogwai.platform.runtime.config.mapping.ConfigMapper} within the
 * project's cyclomatic complexity budget.
 */
final class InputMapper {

    private static final Set<String> INPUT_FIELDS = Set.of("capability", "port");

    private InputMapper() {
        // no instances
    }

    /**
     * Maps the {@code inputs} mapping.
     *
     * @param inputsNode  the inputs node
     * @param path        the structural path
     * @param diagnostics the diagnostics collector
     * @return the mapped input bindings in YAML order
     */
    static List<InputBindingConfig> mapInputs(JsonNode inputsNode, String path, List<Diagnostic> diagnostics) {
        if (inputsNode == null) {
            return List.of();
        }
        if (!inputsNode.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path,
                    "inputs must be a mapping", "provide a mapping of input port to binding"));
            return List.of();
        }
        List<InputBindingConfig> result = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : inputsNode.properties()) {
            String inputPort = entry.getKey();
            if (inputPort.isBlank()) {
                diagnostics.add(Diagnostics.schemaError(Diagnostics.genericChildPath(path),
                        "input binding key must not be blank", "provide a non-blank input port name"));
                continue;
            }
            String bindingPath = Diagnostics.genericChildPath(path);
            InputBindingConfig input = mapInput(entry.getValue(), bindingPath, inputPort, diagnostics);
            if (input != null) {
                result.add(input);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Maps a single input binding.
     *
     * @param input       the input node
     * @param path        the structural path
     * @param inputPort   the target input port name (the mapping key)
     * @param diagnostics the diagnostics collector
     * @return the mapped input binding, or {@code null} if invalid
     */
    static InputBindingConfig mapInput(JsonNode input, String path, String inputPort,
                                      List<Diagnostic> diagnostics) {
        if (input == null || !input.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path, "input binding must be a mapping", "provide a mapping"));
            return null;
        }
        FieldChecks.rejectUnknownFields(input, INPUT_FIELDS, path, diagnostics);
        String capability = FieldChecks.requiredString(input, "capability", path, diagnostics);
        String port = FieldChecks.requiredString(input, "port", path, diagnostics);
        if (capability == null || port == null) {
            return null;
        }
        return new InputBindingConfig(inputPort, capability, port);
    }
}