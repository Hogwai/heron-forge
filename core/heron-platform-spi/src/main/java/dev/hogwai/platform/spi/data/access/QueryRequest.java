package dev.hogwai.platform.spi.data.access;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable description of one data query. */
public record QueryRequest<T>(String operation, String sql, Map<String, ?> parameters, RowMapper<T> mapper) {

    /**
     * Validates and defensively copies the query request values.
     *
     * <p>The operation identifies the logical query, while parameters are the
     * named values supplied to the SQL. The returned map structure and its key-
     * value associations are immutable, but parameter values are retained as
     * supplied rather than copied. A {@code null} value represents SQL NULL.
     */
    public QueryRequest {
        Objects.requireNonNull(operation, "operation must not be null");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        Objects.requireNonNull(sql, "sql must not be null");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        Objects.requireNonNull(parameters, "parameters must not be null");
        Map<String, Object> copiedParameters = new LinkedHashMap<>();
        parameters.forEach((key, value) -> {
            Objects.requireNonNull(key, "parameter key must not be null");
            if (key.isBlank()) {
                throw new IllegalArgumentException("parameter key must not be blank");
            }
            copiedParameters.put(key, value);
        });
        parameters = Collections.unmodifiableMap(copiedParameters);
        Objects.requireNonNull(mapper, "mapper must not be null");
    }
}
