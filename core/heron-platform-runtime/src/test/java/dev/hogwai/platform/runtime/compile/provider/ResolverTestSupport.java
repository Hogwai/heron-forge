package dev.hogwai.platform.runtime.compile.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

/**
 * Package-private support for the resolver tests: an in-memory registry, a
 * configurable fixture factory that counts {@code descriptor()} and
 * {@code validate()} invocations, and small descriptor/config builders.
 */
final class ResolverTestSupport {

    private ResolverTestSupport() {
        // no instances
    }

    static CapabilityConfig config(String id, String providerId, String version,
                                   Map<String, Object> config) {
        return new CapabilityConfig(id, providerId, version, config, List.of());
    }

    static ProviderDescriptor sourceDescriptor(String providerId, String version, ConfigurationSchema schema) {
        return sourceDescriptor(providerId, version, SpiMajor.V1, schema);
    }

    static ProviderDescriptor sourceDescriptor(String providerId, String version, int spiMajor,
                                               ConfigurationSchema schema) {
        return TestProviders.source(providerId, version, spiMajor, "out",
                TestProviders.schema(providerId + "-out", "id", new FieldType.StringType()), schema);
    }

    /**
     * In-memory registry for resolver tests.
     */
    static final class FakeRegistry implements ProviderRegistry {

        private final Map<ProviderId, Registration> registrations = new LinkedHashMap<>();

        void add(ProviderFactory factory) {
            ProviderDescriptor descriptor = factory.descriptor();
            registrations.put(descriptor.providerId(), new Registration(factory, descriptor));
        }

        @Override
        public Optional<Registration> registration(ProviderId providerId) {
            return Optional.ofNullable(registrations.get(providerId));
        }

    }

    /**
     * Configurable fixture factory that counts {@code descriptor()} and
     * {@code validate()} invocations.
     */
    static final class FakeFactory implements ProviderFactory {

        private final ProviderDescriptor descriptor;
        private final List<Diagnostic> validateResult;
        private final boolean throwOnValidate;
        private final boolean nullResult;
        private final boolean nullElement;
        private int descriptorCalls;
        private int validateCalls;

        FakeFactory(ProviderDescriptor descriptor) {
            this(descriptor, List.of(), false, false, false);
        }

        FakeFactory(ProviderDescriptor descriptor, List<Diagnostic> validateResult) {
            this(descriptor, validateResult, false, false, false);
        }

        FakeFactory(ProviderDescriptor descriptor, boolean throwOnValidate, boolean nullResult, boolean nullElement) {
            this(descriptor, List.of(), throwOnValidate, nullResult, nullElement);
        }

        private FakeFactory(ProviderDescriptor descriptor, List<Diagnostic> validateResult,
                            boolean throwOnValidate, boolean nullResult, boolean nullElement) {
            this.descriptor = descriptor;
            this.validateResult = validateResult;
            this.throwOnValidate = throwOnValidate;
            this.nullResult = nullResult;
            this.nullElement = nullElement;
        }

        @Override
        public ProviderDescriptor descriptor() {
            descriptorCalls++;
            ProviderDescriptor d = descriptor;
            return new ProviderDescriptor(d.providerId(), d.version(), d.capabilityKind(), d.spiMajor(),
                    d.inputPorts(), d.outputPorts(), d.configurationSchema());
        }

        @Override
        public List<Diagnostic> validate(Map<String, Object> rawConfig) {
            validateCalls++;
            if (throwOnValidate) {
                throw new IllegalStateException("provider validation exploded");
            }
            if (nullResult) {
                return null;
            }
            if (nullElement) {
                List<Diagnostic> list = new ArrayList<>();
                list.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.WARNING, "/config",
                        "provider warning", null));
                list.add(null);
                list.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, "/config",
                        "provider error", null));
                return list;
            }
            return validateResult;
        }

        @Override
        public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
            throw new UnsupportedOperationException("create is not used in resolver tests");
        }

        int descriptorCalls() {
            return descriptorCalls;
        }

        int validateCalls() {
            return validateCalls;
        }
    }
}
