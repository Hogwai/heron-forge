package dev.hogwai.platform.spi;

/**
 * Major version of the SPI contract.
 *
 * <p>Exposes the SPI major version as an integer constant, accessible directly
 * as {@code SpiMajor.V1}.
 */
public final class SpiMajor {

    /** The first major version of the SPI contract. */
    public static final int V1 = 1;

    private SpiMajor() {
        // no instances
    }
}
