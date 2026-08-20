package dev.hogwai.platform.host.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal copier and validator for structured payload values. */
final class StructuredValues {

    private StructuredValues() {
    }

    static Map<String, Object> copyMap(Map<String, Object> value) {
        Objects.requireNonNull(value, "value must not be null");
        return copyMap(value, new IdentityHashMap<>());
    }

    private static Map<String, Object> copyMap(Map<?, ?> value, IdentityHashMap<Object, Boolean> activeValues) {
        enter(value, activeValues);
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : value.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("structured maps must use String keys");
                }
                copy.put(key, copyValue(entry.getValue(), activeValues));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            activeValues.remove(value);
        }
    }

    private static List<Object> copyList(List<?> value, IdentityHashMap<Object, Boolean> activeValues) {
        enter(value, activeValues);
        try {
            List<Object> copy = new ArrayList<>(value.size());
            for (Object item : value) {
                copy.add(copyValue(item, activeValues));
            }
            return Collections.unmodifiableList(copy);
        } finally {
            activeValues.remove(value);
        }
    }

    private static Object copyValue(Object value, IdentityHashMap<Object, Boolean> activeValues) {
        return switch (value) {
            case null -> null;
            case Boolean booleanValue -> booleanValue;
            case Number numberValue -> numberValue;
            case String stringValue -> stringValue;
            case Map<?, ?> map -> copyMap(map, activeValues);
            case List<?> list -> copyList(list, activeValues);
            default -> throw new IllegalArgumentException(
                    "unsupported structured value type: " + value.getClass().getName());
        };
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> activeValues) {
        if (activeValues.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("structured payload values must not be cyclic");
        }
    }
}
