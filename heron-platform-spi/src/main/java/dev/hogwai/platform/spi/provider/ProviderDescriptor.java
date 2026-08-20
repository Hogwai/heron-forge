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
 * <p>A descriptor carries the provider identity and version, the
 * {@link CapabilityKind}, the SPI major version, immutable maps of input and
 * output {@link PortDescriptor}s, a {@link ConfigurationSchema}, a
 * {@code deterministic} flag and a {@link ThreadSafety} contract.
 *
 * <p>Shape constraints are enforced at construction: the output port map must
 * not be empty, a {@link CapabilityKind#SOURCE} capability must not declare
 * input ports, and every map key must equal the {@code portId()} of its
 * descriptor. Framework-independent and immutable.
 */
@SuppressWarnings("java:S6206")
public final class ProviderDescriptor {

    /**
     * Thread-safety contract of a capability instance.
     */
    public enum ThreadSafety {
        /** Instances may be invoked concurrently from multiple threads. */
        THREAD_SAFE,
        /** Instances must not be invoked concurrently. */
        NOT_THREAD_SAFE
    }

    private final ProviderId providerId;
    private final ProviderVersion version;
    private final CapabilityKind capabilityKind;
    private final int spiMajor;
    private final Map<PortId, PortDescriptor> inputPorts;
    private final Map<PortId, PortDescriptor> outputPorts;
    private final ConfigurationSchema configurationSchema;
    private final boolean deterministic;
    private final ThreadSafety threadSafety;

    /**
     * Creates a provider descriptor.
     *
     * <p>The nine parameters are real coordinates/contracts of the descriptor
     * (identity, version, kind, SPI major, port maps, schema, determinism and
     * thread-safety contract); no artificial builder is introduced, so the
     * constructor is intentionally kept as the single public entry point.
     *
     * @param providerId         the provider identifier
     * @param version            the provider version
     * @param capabilityKind     the capability kind
     * @param spiMajor           the SPI major version
     * @param inputPorts         the input ports keyed by {@link PortId}
     * @param outputPorts        the output ports keyed by {@link PortId}
     * @param configurationSchema the configuration schema
     * @param deterministic      whether the capability is deterministic
     * @param threadSafety       the thread-safety contract
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code spiMajor} is not strictly
     *                                  positive, if {@code outputPorts} is empty,
     *                                  if a {@code SOURCE} declares input ports,
     *                                  or if a port map key does not match its
     *                                  descriptor's {@code portId()}
     */
    @SuppressWarnings("java:S107") // Nine real descriptor coordinates/contracts; no artificial builder introduced.
    public ProviderDescriptor(ProviderId providerId, ProviderVersion version, CapabilityKind capabilityKind,
                              int spiMajor, Map<PortId, PortDescriptor> inputPorts,
                              Map<PortId, PortDescriptor> outputPorts, ConfigurationSchema configurationSchema,
                              boolean deterministic, ThreadSafety threadSafety) {
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
        this.deterministic = deterministic;
        this.threadSafety = Objects.requireNonNull(threadSafety, "threadSafety must not be null");
    }

    /**
     * Returns the provider identifier.
     *
     * @return the provider identifier
     */
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider version.
     *
     * @return the provider version
     */
    public ProviderVersion version() {
        return version;
    }

    /**
     * Returns the capability kind.
     *
     * @return the capability kind
     */
    public CapabilityKind capabilityKind() {
        return capabilityKind;
    }

    /**
     * Returns the SPI major version.
     *
     * @return the SPI major version
     */
    public int spiMajor() {
        return spiMajor;
    }

    /**
     * Returns an immutable view of the input ports.
     *
     * @return the input ports
     */
    public Map<PortId, PortDescriptor> inputPorts() {
        return inputPorts;
    }

    /**
     * Returns an immutable view of the output ports.
     *
     * @return the output ports
     */
    public Map<PortId, PortDescriptor> outputPorts() {
        return outputPorts;
    }

    /**
     * Returns the configuration schema.
     *
     * @return the configuration schema
     */
    public ConfigurationSchema configurationSchema() {
        return configurationSchema;
    }

    /**
     * Returns whether the capability is deterministic.
     *
     * @return whether the capability is deterministic
     */
    public boolean deterministic() {
        return deterministic;
    }

    /**
     * Returns the thread-safety contract.
     *
     * @return the thread-safety contract
     */
    public ThreadSafety threadSafety() {
        return threadSafety;
    }

    /**
     * Private nested validator for {@link ProviderDescriptor}.
     *
     * <p>Kept as a private nested validator so that the public class stays
     * within the project's cyclomatic complexity budget.
     */
    private static final class Validator {

        private Validator() {
            // no instances
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
