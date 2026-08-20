package dev.hogwai.platform.runtime.load.config.safe;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cohesive public gate for converting, validating and deep-copying safe
 * configuration values.
 *
 * <p>Only v1 scalar types ({@link String}, {@link Boolean}, {@link Long},
 * {@link java.math.BigDecimal} and {@code null}), lists and maps with
 * {@link String} keys are accepted. Any other Java type (including
 * provider-specific objects and arrays) is rejected. Every collection is
 * recursively copied and exposed through an unmodifiable view.
 */
public final class SafeConfig {

    private SafeConfig() {
        // no instances
    }

    /**
     * Deep-copies and validates a configuration value tree.
     *
     * @param config the configuration values
     * @return an immutable deep copy
     * @throws IllegalArgumentException if a key is not a {@link String} or a
     *                                  value is not a supported v1 type
     */
    public static Map<String, Object> copy(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : config.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("config map keys must be String");
            }
            result.put(key, SafeConfigValue.copyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Converts a Jackson {@link JsonNode} subtree into an immutable tree of
     * safe values.
     *
     * @param node the config node
     * @return an immutable map of safe values
     * @throws IllegalArgumentException if the node contains an unsupported
     *                                  value type
     */
    public static Map<String, Object> fromJsonNode(JsonNode node) {
        return SafeValues.fromObject(node);
    }
}
