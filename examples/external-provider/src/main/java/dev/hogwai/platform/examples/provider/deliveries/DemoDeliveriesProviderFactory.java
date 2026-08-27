package dev.hogwai.platform.examples.provider.deliveries;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.examples.provider.postgres.SupplyChainDatabaseConfig;
import dev.hogwai.platform.examples.provider.support.ExecutionSupport;
import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

/**
 * Deterministic source of the example delivery records.
 */
@HeronService(value = ProviderFactory.class, id = "demo.deliveries")
public final class DemoDeliveriesProviderFactory implements ProviderFactory {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            new ProviderId("demo.deliveries"), ProviderVersion.parse("1.0.0"), CapabilityKind.SOURCE, SpiMajor.V1,
            Map.of(), Map.of(new PortId("records"), new PortDescriptor(
            new PortId("records"), SupplyChainSchemas.deliveries(), true)),
            SupplyChainDatabaseConfig.databaseConfigSchema());

    /**
     * Creates the deliveries factory.
     */
    public DemoDeliveriesProviderFactory() {
        // Default constructor
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return SupplyChainDatabaseConfig.validate(rawConfig, "deliveries");
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        Objects.requireNonNull(context, "context must not be null");
        List<Diagnostic> diagnostics = validate(rawConfig);
        if (!diagnostics.isEmpty()) {
            throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, diagnostics);
        }
        DataAccess dataAccess = context.dataAccessFactory().open(SupplyChainDatabaseConfig.from(rawConfig));
        try {
            context.resourceTracker().register(dataAccess);
        } catch (RuntimeException registrationFailure) {
            try {
                dataAccess.close();
            } catch (RuntimeException closeFailure) {
                registrationFailure.addSuppressed(closeFailure);
            }
            throw registrationFailure;
        }
        return (inputs, executionContext) -> {
            ExecutionSupport.checkExecution(executionContext);
            Objects.requireNonNull(inputs, "inputs must not be null");
            QueryContext queryContext = new QueryContext(executionContext.deadline(),
                    executionContext.cancellationToken()::isCancellationRequested);
            return DeliveriesQuery.read(dataAccess, queryContext, SupplyChainDatabaseConfig.limits(rawConfig));
        };
    }
}
