package dev.hogwai.platform.host.api;

import java.util.Map;

/** Immutable structured data returned by a host application invocation.
 *
 * @param value structured object represented as a string-keyed map
 */
public record StructuredPayload(Map<String, Object> value) {

    /** Copies and validates the complete structured value. */
    public StructuredPayload {
        value = StructuredValues.copyMap(value);
    }
}
