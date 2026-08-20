package dev.hogwai.platform.spi.data;

import java.util.Objects;

/**
 * Immutable metadata describing a {@link MaterializedDataSet}.
 *
 * <p>Framework-independent and immutable.
 *
 * @param name   the non-blank dataset name
 * @param limits the dataset limits
 */
public record DataSetMetadata(String name, DataSetLimits limits) {

    /**
     * Compact constructor enforcing the metadata contract.
     *
     * @throws NullPointerException     if {@code name} or {@code limits} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public DataSetMetadata {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(limits, "limits must not be null");
    }
}