package dev.hogwai.platform.runtime.load.config.safe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursively copies and validates a single configuration value.
 *
 * <p>Package-private helper that keeps {@link SafeConfig} within the project's
 * cyclomatic complexity budget.
 */
final class SafeConfigValue {

    private SafeConfigValue() {
        // no instances
    }

    static Object copyValue(Object value) {
        if (SafeScalars.isScalar(value)) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        if (value instanceof List<?> list) {
            return copyList(list);
        }
        throw new IllegalArgumentException("unsupported config value type: " + value.getClass().getName());
    }

    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("config map keys must be String");
            }
            result.put(key, copyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<Object> copyList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            result.add(copyValue(item));
        }
        return Collections.unmodifiableList(result);
    }
}
