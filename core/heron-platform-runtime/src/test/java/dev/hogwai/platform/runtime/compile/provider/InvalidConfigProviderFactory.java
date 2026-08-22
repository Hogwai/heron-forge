package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;

import java.util.List;
import java.util.Map;

/**
 * Fixture provider factory whose provider-level validation always fails.
 */
public final class InvalidConfigProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("invalid-config", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("invalid-config-out", "id", new FieldType.StringType()),
                TestProviders.emptyConfigSchema());
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, "/config",
                "provider rejected the configuration", "check the provider configuration"));
    }
}
