package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderDescriptorTest {

    private static final ProviderId PROVIDER = new ProviderId("acme");
    private static final ProviderVersion VERSION = ProviderVersion.parse("1.2.3");
    private static final Map<PortId, PortDescriptor> OUTPUT =
            Map.of(new PortId("out"), ProviderTestSupport.port("out"));

    private static ConfigurationSchema config() {
        return new ConfigurationSchema(Set.of("host"), Set.of("host"),
                Map.of("host", ConfigurationSchema.ScalarKind.STRING), Map.of());
    }

    private static ProviderDescriptor descriptor(CapabilityKind kind, int spiMajor,
            Map<PortId, PortDescriptor> inputs, Map<PortId, PortDescriptor> outputs,
            boolean deterministic, ProviderDescriptor.ThreadSafety threadSafety) {
        return new ProviderDescriptor(PROVIDER, VERSION, kind, spiMajor, inputs, outputs,
                config(), deterministic, threadSafety);
    }

    private static ProviderDescriptor source() {
        return descriptor(CapabilityKind.SOURCE, SpiMajor.V1, Map.of(), OUTPUT,
                true, ProviderDescriptor.ThreadSafety.THREAD_SAFE);
    }

    private static ProviderDescriptor transform() {
        return descriptor(CapabilityKind.TRANSFORM, SpiMajor.V1,
                Map.of(new PortId("in"), ProviderTestSupport.port("in")), OUTPUT,
                false, ProviderDescriptor.ThreadSafety.NOT_THREAD_SAFE);
    }

    @Test
    void acceptsValidSource() {
        ProviderDescriptor descriptor = source();
        assertThat(descriptor.providerId()).isEqualTo(PROVIDER);
        assertThat(descriptor.version()).isEqualTo(VERSION);
        assertThat(descriptor.capabilityKind()).isEqualTo(CapabilityKind.SOURCE);
        assertThat(descriptor.spiMajor()).isEqualTo(SpiMajor.V1);
        assertThat(descriptor.inputPorts()).isEmpty();
        assertThat(descriptor.outputPorts()).containsKey(new PortId("out"));
        assertThat(descriptor.deterministic()).isTrue();
        assertThat(descriptor.threadSafety()).isEqualTo(ProviderDescriptor.ThreadSafety.THREAD_SAFE);
    }

    @Test
    void acceptsValidTransform() {
        ProviderDescriptor descriptor = transform();
        assertThat(descriptor.capabilityKind()).isEqualTo(CapabilityKind.TRANSFORM);
        assertThat(descriptor.inputPorts()).containsKey(new PortId("in"));
        assertThat(descriptor.outputPorts()).containsKey(new PortId("out"));
        assertThat(descriptor.deterministic()).isFalse();
    }

    @Test
    void rejectsEmptyOutputPorts() {
        Map<PortId, PortDescriptor> noInputs = Map.of();
        Map<PortId, PortDescriptor> noOutputs = Map.of();
        assertThatThrownBy(() -> descriptor(CapabilityKind.TRANSFORM, SpiMajor.V1,
                noInputs, noOutputs, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSourceWithInputPorts() {
        Map<PortId, PortDescriptor> sourceInputs = Map.of(new PortId("in"), ProviderTestSupport.port("in"));
        assertThatThrownBy(() -> descriptor(CapabilityKind.SOURCE, SpiMajor.V1,
                sourceInputs, OUTPUT, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTransformWithoutOutput() {
        Map<PortId, PortDescriptor> transformInputs = Map.of(new PortId("in"), ProviderTestSupport.port("in"));
        Map<PortId, PortDescriptor> noOutputs = Map.of();
        assertThatThrownBy(() -> descriptor(CapabilityKind.TRANSFORM, SpiMajor.V1,
                transformInputs, noOutputs, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPortKeyMismatch() {
        Map<PortId, PortDescriptor> noInputs = Map.of();
        Map<PortId, PortDescriptor> mismatchedOutputs = Map.of(new PortId("other"), ProviderTestSupport.port("out"));
        assertThatThrownBy(() -> descriptor(CapabilityKind.SOURCE, SpiMajor.V1,
                noInputs, mismatchedOutputs, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveSpiMajor() {
        Map<PortId, PortDescriptor> noInputs = Map.of();
        assertThatThrownBy(() -> descriptor(CapabilityKind.SOURCE, 0,
                noInputs, OUTPUT, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullArguments() {
        Map<PortId, PortDescriptor> noInputs = Map.of();
        ConfigurationSchema schema = config();
        assertThatThrownBy(() -> new ProviderDescriptor(null, VERSION, CapabilityKind.SOURCE, SpiMajor.V1,
                noInputs, OUTPUT, schema, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderDescriptor(PROVIDER, VERSION, CapabilityKind.SOURCE, SpiMajor.V1,
                null, OUTPUT, schema, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderDescriptor(PROVIDER, VERSION, CapabilityKind.SOURCE, SpiMajor.V1,
                noInputs, null, schema, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderDescriptor(PROVIDER, VERSION, CapabilityKind.SOURCE, SpiMajor.V1,
                noInputs, OUTPUT, null, true, ProviderDescriptor.ThreadSafety.THREAD_SAFE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderDescriptor(PROVIDER, VERSION, CapabilityKind.SOURCE, SpiMajor.V1,
                noInputs, OUTPUT, schema, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void portMapsAreImmutable() {
        ProviderDescriptor descriptor = source();
        PortId extraId = new PortId("x");
        PortDescriptor extraPort = ProviderTestSupport.port("x");
        Map<PortId, PortDescriptor> outputView = descriptor.outputPorts();
        Map<PortId, PortDescriptor> inputView = descriptor.inputPorts();
        assertThatThrownBy(() -> outputView.put(extraId, extraPort))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> inputView.put(extraId, extraPort))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void threadSafetyEnumExposesBothValues() {
        assertThat(ProviderDescriptor.ThreadSafety.values())
                .containsExactly(ProviderDescriptor.ThreadSafety.THREAD_SAFE,
                        ProviderDescriptor.ThreadSafety.NOT_THREAD_SAFE);
    }
}