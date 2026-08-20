package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory whose descriptor accessor throws.
 */
public final class ThrowingDescriptorProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        throw new IllegalStateException("descriptor unavailable");
    }
}