package dev.hogwai.platform.spi.data;

/**
 * Immutable limits applied to a {@link MaterializedDataSet}.
 *
 * <p>Both limits are strictly positive. Framework-independent and immutable.
 *
 * @param maxRows  the maximum number of rows
 * @param maxBytes the maximum estimated size in bytes
 */
public record DataSetLimits(long maxRows, long maxBytes) {

    /**
     * Compact constructor enforcing the limits contract.
     *
     * @throws IllegalArgumentException if {@code maxRows} or {@code maxBytes} is
     *                                  not strictly positive
     */
    public DataSetLimits {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be strictly positive");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be strictly positive");
        }
    }
}
