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
 * Provider fixture that deliberately duplicates the graph cycle diagnostic.
 */
public final class DuplicateCycleDiagnosticProviderFactory extends AbstractFixtureFactory {

    @Override
    public ProviderDescriptor descriptor() {
        return TestProviders.source("duplicate-cycle", "1.0.0", SpiMajor.V1, "out",
                TestProviders.schema("duplicate-cycle-out", "id", new FieldType.StringType()),
                TestProviders.hostConfigSchema());
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return List.of(new Diagnostic(PlatformErrorCode.GRAPH_CYCLE_ERROR, Severity.ERROR, null,
                "graph contains a cycle involving capabilities: a, b", "remove the cyclic dependency"));
    }
}
