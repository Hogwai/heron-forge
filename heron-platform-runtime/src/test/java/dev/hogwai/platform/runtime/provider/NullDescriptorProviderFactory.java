package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory that returns a null descriptor.
 */
public final class NullDescriptorProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return null;
    }
}