package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** In-memory provider fixture with configurable create and validate behavior. */
final class SnapshotBuilderTestProviderFactory implements ProviderFactory {

    private final ProviderDescriptor descriptor;
    private final Function<BuildContext, CapabilityInstance> creator;
    private final List<Diagnostic> validationDiagnostics;
    private final List<BuildContext> contexts = new ArrayList<>();
    private final List<CapabilityInstance> created = new ArrayList<>();

    SnapshotBuilderTestProviderFactory(ProviderDescriptor descriptor,
            Function<BuildContext, CapabilityInstance> creator) {
        this(descriptor, creator, List.of());
    }

    SnapshotBuilderTestProviderFactory(ProviderDescriptor descriptor,
            Function<BuildContext, CapabilityInstance> creator, List<Diagnostic> validationDiagnostics) {
        this.descriptor = descriptor;
        this.creator = creator;
        this.validationDiagnostics = validationDiagnostics;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return validationDiagnostics;
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        contexts.add(context);
        CapabilityInstance instance = creator.apply(context);
        if (instance != null) {
            created.add(instance);
        }
        return instance;
    }

    List<BuildContext> contexts() {
        return contexts;
    }

    List<CapabilityInstance> created() {
        return created;
    }
}
