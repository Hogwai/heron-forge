package dev.hogwai.platform.examples.provider.exceptions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

/** SPI factory for the supply-chain exception detector. */
@HeronService(value = ProviderFactory.class, id = "supply-chain.exception-detector")
public final class SupplyChainExceptionDetectorFactory implements ProviderFactory {

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            new ProviderId("supply-chain.exception-detector"), ProviderVersion.parse("1.0.0"),
            CapabilityKind.TRANSFORM, SpiMajor.V1,
            Map.of(new PortId("orders"), new PortDescriptor(new PortId("orders"), SupplyChainSchemas.orders(), true),
                    new PortId("deliveries"),
                    new PortDescriptor(new PortId("deliveries"), SupplyChainSchemas.deliveries(), true)),
            Map.of(new PortId("records"),
                    new PortDescriptor(new PortId("records"), SupplyChainSchemas.exceptions(), true)),
            DetectorConfig.configurationSchema());

    /** Creates the detector factory. */
    public SupplyChainExceptionDetectorFactory() {
        // intentionally empty
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        return DetectorConfig.validate(rawConfig);
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        Objects.requireNonNull(context, "context must not be null");
        List<Diagnostic> diagnostics = validate(rawConfig);
        if (!diagnostics.isEmpty()) {
            throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, diagnostics);
        }
        return new ExceptionDetector(DetectorConfig.from(rawConfig), context.clock());
    }
}
