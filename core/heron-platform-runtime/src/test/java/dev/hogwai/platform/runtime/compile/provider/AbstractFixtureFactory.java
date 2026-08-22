package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import java.util.List;
import java.util.Map;

/**
 * Base class for the Task 6 fixture provider factories.
 *
 * <p>Concrete subclasses are public with public no-arg constructors so that
 * they can be instantiated by the Java {@link java.util.ServiceLoader}.
 */
abstract class AbstractFixtureFactory implements ProviderFactory {

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return List.of();
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        throw new UnsupportedOperationException("create is not used in Task 6");
    }
}
