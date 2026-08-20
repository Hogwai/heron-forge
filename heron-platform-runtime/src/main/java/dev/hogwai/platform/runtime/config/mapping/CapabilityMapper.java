package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.capability.CapabilityConfig;
import dev.hogwai.platform.runtime.config.input.InputBindingConfig;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.runtime.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the {@code spec.capabilities} list and each capability declaration.
 *
 * <p>Package-private helper that keeps the public
 * {@link dev.hogwai.platform.runtime.config.ConfigValidator} within the
 * project's cyclomatic complexity budget.
 */
final class CapabilityMapper {

    private static final Set<String> CAPABILITY_FIELDS = Set.of("id", "type", "provider", "config", "inputs");

    private CapabilityMapper() {
        // no instances
    }

    /**
     * Maps the {@code capabilities} list.
     *
     * @param capabilitiesNode the capabilities node
     * @param diagnostics      the diagnostics collector
     * @return the mapped capabilities
     */
    static List<CapabilityConfig> mapCapabilities(JsonNode capabilitiesNode, List<Diagnostic> diagnostics) {
        if (capabilitiesNode == null) {
            diagnostics.add(Diagnostics.schemaError("/spec/capabilities",
                    "missing required member 'capabilities'", "add a capabilities list"));
            return List.of();
        }
        if (!capabilitiesNode.isArray()) {
            diagnostics.add(Diagnostics.schemaError("/spec/capabilities",
                    "capabilities must be a list", "provide a list"));
            return List.of();
        }
        List<CapabilityConfig> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < capabilitiesNode.size(); i++) {
            String path = "/spec/capabilities/" + i;
            CapabilityConfig mapped = mapCapability(capabilitiesNode.get(i), path, diagnostics);
            if (mapped != null && !seenIds.add(mapped.id())) {
                diagnostics.add(Diagnostics.schemaError(path + "/id",
                        "duplicate capability id", "use a unique id"));
            }
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Maps a single capability declaration.
     *
     * @param cap         the capability node
     * @param path        the structural path
     * @param diagnostics the diagnostics collector
     * @return the mapped capability, or {@code null} if invalid
     */
    static CapabilityConfig mapCapability(JsonNode cap, String path, List<Diagnostic> diagnostics) {
        if (cap == null || !cap.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path, "capability must be a mapping", "provide a mapping"));
            return null;
        }
        FieldChecks.rejectUnknownFields(cap, CAPABILITY_FIELDS, path, diagnostics);

        String id = FieldChecks.requiredString(cap, "id", path, diagnostics);
        CapabilityKind type = FieldChecks.parseKind(
                FieldChecks.requiredString(cap, "type", path, diagnostics), path + "/type", diagnostics);
        ProviderRef provider = ProviderMapper.mapProvider(cap.get("provider"), path + "/provider", diagnostics);
        Map<String, Object> config = ConfigMapper.mapConfig(cap.get("config"), path + "/config", diagnostics);
        List<InputBindingConfig> inputs = InputMapper.mapInputs(cap.get("inputs"), path + "/inputs", diagnostics);

        if (id == null || type == null || provider == null) {
            return null;
        }
        return new CapabilityConfig(id, type, provider.id(), provider.version(), config, inputs);
    }
}
