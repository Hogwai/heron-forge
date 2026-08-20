package dev.hogwai.platform.runtime.load.config.safe;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a Jackson {@link JsonNode} subtree into an immutable tree of safe
 * values (strings, canonical {@code long}/{@link java.math.BigDecimal}
 * numbers, booleans, {@code null}, lists and maps).
 *
 * <p>No provider-specific Java types are ever deserialized; only plain scalar,
 * list and mapping values are produced. Every collection is defensively copied
 * and exposed through an unmodifiable view.
 */
final class SafeValues {

    private SafeValues() {
        // no instances
    }

    static Map<String, Object> fromObject(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), fromValue(entry.getValue())));
        return Collections.unmodifiableMap(map);
    }

    static Object fromValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(fromValue(item)));
            return Collections.unmodifiableList(list);
        }
        if (node.isObject()) {
            return fromObject(node);
        }
        throw new IllegalArgumentException("unsupported config value node: " + node.getNodeType());
    }
}
