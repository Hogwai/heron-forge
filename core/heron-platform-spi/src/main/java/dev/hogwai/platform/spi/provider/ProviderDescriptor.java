package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of a provider capability.
 *
 * <p>A descriptor carries provider identity and version, capability kind, SPI
 * major version, immutable input and output port maps, and configuration
 * schema. Shape constraints are enforced at construction.
 */
public record ProviderDescriptor(ProviderId providerId,
                                 ProviderVersion version,
                                 CapabilityKind capabilityKind,
                                 int spiMajor,
                                 Map<PortId, PortDescriptor> inputPorts,
                                 Map<PortId, PortDescriptor> outputPorts,
                                 ConfigurationSchema configurationSchema) {

    /**
     * Creates a provider descriptor.
     *
     * @param providerId          provider identifier
     * @param version             provider version
     * @param capabilityKind      capability kind
     * @param spiMajor            SPI major version
     * @param inputPorts          input ports keyed by {@link PortId}
     * @param outputPorts         output ports keyed by {@link PortId}
     * @param configurationSchema configuration schema
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the SPI major is not positive, output
     *                                  ports are empty, a source declares inputs, or a port key does not
     *                                  match its descriptor's port id
     */
    @SuppressWarnings("java:S107")
    public ProviderDescriptor(ProviderId providerId, ProviderVersion version, CapabilityKind capabilityKind,
                              int spiMajor, Map<PortId, PortDescriptor> inputPorts,
                              Map<PortId, PortDescriptor> outputPorts, ConfigurationSchema configurationSchema) {
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.version = Objects.requireNonNull(version, "version must not be null");
        this.capabilityKind = Objects.requireNonNull(capabilityKind, "capabilityKind must not be null");
        Objects.requireNonNull(inputPorts, "inputPorts must not be null");
        Objects.requireNonNull(outputPorts, "outputPorts must not be null");
        Validator.validate(capabilityKind, spiMajor, inputPorts, outputPorts);
        this.spiMajor = spiMajor;
        this.inputPorts = Map.copyOf(inputPorts);
        this.outputPorts = Map.copyOf(outputPorts);
        this.configurationSchema = Objects.requireNonNull(configurationSchema, "configurationSchema must not be null");
    }

    private static final class Validator {

        private Validator() {
        }

        static void validate(CapabilityKind capabilityKind, int spiMajor,
                             Map<PortId, PortDescriptor> inputPorts, Map<PortId, PortDescriptor> outputPorts) {
            if (spiMajor <= 0) {
                throw new IllegalArgumentException("spiMajor must be strictly positive");
            }
            if (outputPorts.isEmpty()) {
                throw new IllegalArgumentException("outputPorts must not be empty");
            }
            if (capabilityKind == CapabilityKind.SOURCE && !inputPorts.isEmpty()) {
                throw new IllegalArgumentException("SOURCE capability must not declare input ports");
            }
            validatePortMap(inputPorts, "inputPorts");
            validatePortMap(outputPorts, "outputPorts");
        }

        private static void validatePortMap(Map<PortId, PortDescriptor> ports, String label) {
            for (Map.Entry<PortId, PortDescriptor> entry : ports.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().portId())) {
                    throw new IllegalArgumentException(label + " key must match its PortDescriptor.portId()");
                }
            }
        }
    }
}
