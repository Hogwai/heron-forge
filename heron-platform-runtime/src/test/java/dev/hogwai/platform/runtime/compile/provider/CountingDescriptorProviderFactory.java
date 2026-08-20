package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory whose {@code descriptor()} returns a fresh
 * descriptor instance on every call and counts the calls.
 *
 * <p>Used to prove that the registry reads each factory descriptor exactly once
 * per instance and that lookups never re-read it.
 */
public final class CountingDescriptorProviderFactory extends AbstractFixtureFactory {

    private static int descriptorCalls;
    private static ProviderDescriptor firstDescriptor;

    /**
     * @return the number of {@code descriptor()} invocations since the last reset
     */
    public static int descriptorCalls() {
        return descriptorCalls;
    }

    /**
     * @return the first descriptor returned since the last reset, or {@code null}
     */
    public static ProviderDescriptor firstDescriptor() {
        return firstDescriptor;
    }

    /**
     * Resets the call counter and the recorded first descriptor.
     */
    public static void reset() {
        descriptorCalls = 0;
        firstDescriptor = null;
    }

    @Override
    public ProviderDescriptor descriptor() {
        descriptorCalls++;
        ProviderDescriptor descriptor = TestProviders.source("counting", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("counting-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
        if (firstDescriptor == null) {
            firstDescriptor = descriptor;
        }
        return descriptor;
    }
}