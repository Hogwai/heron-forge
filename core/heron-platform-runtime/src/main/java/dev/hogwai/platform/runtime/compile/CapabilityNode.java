package dev.hogwai.platform.runtime.compile;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable node of a compiled capability graph.
 *
 * <p>A node carries the capability identity, kind, the resolved provider
 * identity, version, factory and descriptor, the raw configuration and the
 * ordered list of input {@link PortBinding}s. The input and output port maps
 * are exposed through the provider descriptor.
 *
 * <p>Nodes are produced by the {@link GraphCompiler}; the constructor is
 * private. During compilation a node is created empty and its input bindings
 * are installed once with {@link #setInputs(List)} so that every
 * {@link PortBinding} references the exact node instances later exposed by the
 * {@link CapabilityGraph}; after compilation the node is immutable.
 */
public final class CapabilityNode {

    /**
     * Resolved provider identity and implementation for a node.
     *
     * <p>Groups the provider id, version, factory and descriptor, which always
     * travel together, so the node constructor stays within the parameter
     * budget.
     */
    public record Provider(ProviderId providerId, ProviderVersion version,
                           ProviderFactory factory, ProviderDescriptor descriptor) {

        public Provider {
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(version, "version must not be null");
            Objects.requireNonNull(factory, "factory must not be null");
            Objects.requireNonNull(descriptor, "descriptor must not be null");
        }
    }

    private final String id;
    private final CapabilityKind kind;
    private final Provider provider;
    private final Map<String, Object> config;
    private List<PortBinding> inputs;

    private CapabilityNode(String id, CapabilityKind kind, Provider provider,
                           Map<String, Object> config, List<PortBinding> inputs) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.config = Map.copyOf(config);
        this.inputs = List.copyOf(inputs);
    }

    /**
     * Creates an empty node used by the compiler before the canonical bindings
     * are resolved.
     *
     * @param id         the capability id
     * @param kind       the capability kind
     * @param providerId the resolved provider id
     * @param version    the resolved provider version
     * @param factory    the resolved provider factory
     * @param descriptor the resolved provider descriptor
     * @param config     the raw configuration
     * @return the empty node
     */
    static CapabilityNode mutable(String id, CapabilityKind kind, ProviderId providerId, ProviderVersion version,
                                  ProviderFactory factory, ProviderDescriptor descriptor, Map<String, Object> config) {
        return new CapabilityNode(id, kind, new Provider(providerId, version, factory, descriptor), config, List.of());
    }

    /**
     * Installs the input bindings, making the node immutable.
     *
     * @param newInputs the input bindings
     * @throws NullPointerException if {@code newInputs} is {@code null}
     */
    void setInputs(List<PortBinding> newInputs) {
        this.inputs = List.copyOf(Objects.requireNonNull(newInputs, "newInputs must not be null"));
    }

    /**
     * Returns the capability id.
     *
     * @return the capability id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the capability kind.
     *
     * @return the capability kind
     */
    public CapabilityKind kind() {
        return kind;
    }

    /**
     * Returns the resolved provider id.
     *
     * @return the provider id
     */
    public ProviderId providerId() {
        return provider.providerId();
    }

    /**
     * Returns the resolved provider version.
     *
     * @return the provider version
     */
    public ProviderVersion version() {
        return provider.version();
    }

    /**
     * Returns the resolved provider factory.
     *
     * @return the provider factory
     */
    public ProviderFactory factory() {
        return provider.factory();
    }

    /**
     * Returns the resolved provider descriptor.
     *
     * @return the provider descriptor
     */
    public ProviderDescriptor descriptor() {
        return provider.descriptor();
    }

    /**
     * Returns an immutable view of the raw configuration.
     *
     * @return the raw configuration
     */
    public Map<String, Object> config() {
        return config;
    }

    /**
     * Returns an immutable view of the input bindings.
     *
     * @return the input bindings
     */
    public List<PortBinding> inputs() {
        return inputs;
    }

    /**
     * Returns the input ports declared by the provider descriptor.
     *
     * @return the input ports
     */
    public Map<PortId, PortDescriptor> inputPorts() {
        return provider.descriptor().inputPorts();
    }

    /**
     * Returns the output ports declared by the provider descriptor.
     *
     * @return the output ports
     */
    public Map<PortId, PortDescriptor> outputPorts() {
        return provider.descriptor().outputPorts();
    }
}