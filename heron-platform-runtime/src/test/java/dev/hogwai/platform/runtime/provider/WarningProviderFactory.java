package dev.hogwai.platform.runtime.provider;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ValidationContext;
import java.util.List;
import java.util.Map;

/**
 * Fixture provider factory whose descriptor uses a deprecated configuration
 * field (producing a generic deprecation warning) and whose provider-level
 * validation always returns a warning.
 *
 * <p>Used to prove that resolver warnings are preserved and path-rewritten by
 * the graph compiler.
 */
public final class WarningProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("warning-provider", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("warning-provider-out", "id", new FieldType.StringType()),
                TestProviders.deprecatedHostConfigSchema());
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig, ValidationContext context) {
        return List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.WARNING, "/config",
                "provider configuration warning", "check the provider configuration"));
    }
}