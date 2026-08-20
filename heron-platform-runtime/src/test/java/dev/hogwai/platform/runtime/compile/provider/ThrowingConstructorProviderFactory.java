package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.provider.ProviderDescriptor;

/**
 * Fixture provider factory whose constructor throws.
 *
 * <p>Used to prove that a {@link java.util.ServiceConfigurationError} raised
 * while instantiating a provider is reported with a safe service name and
 * without leaking the constructor failure detail.
 */
public final class ThrowingConstructorProviderFactory extends AbstractFixtureFactory {

    /**
     * Throws immediately so that the ServiceLoader cannot instantiate the
     * provider.
     */
    public ThrowingConstructorProviderFactory() {
        throw new IllegalStateException("cannot construct");
    }

    @Override
    public ProviderDescriptor descriptor() {
        throw new UnsupportedOperationException("never reached");
    }
}