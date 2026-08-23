package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.FakeFactory;
import dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.FakeRegistry;
import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.config;
import static dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.sourceDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProviderResolver} resolution semantics.
 *
 * <p>Proves exact provider/version resolution, the
 * {@code PROVIDER_NOT_FOUND}/{@code PROVIDER_VERSION_MISMATCH} distinction, the
 * SPI major checks, descriptor-derived capability kinds, warning aggregation and the absence of
 * raw configuration in the public result.
 */
class ProviderResolverTest {

    private final FakeRegistry registry = new FakeRegistry();
    private final ProviderResolver resolver = new ProviderResolver(registry);

    @Test
    void resolvesExactProvider() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()));
        registry.add(factory);
        ProviderDescriptor descriptor = registry.registration(new ProviderId("orders")).orElseThrow().descriptor();

        ProviderResolver.ResolvedProvider resolved = resolver.resolve(
                config("orders", "orders", "1.0.0", Map.of("host", "localhost")));

        assertThat(resolved.factory()).isSameAs(factory);
        assertThat(resolved.descriptor()).isSameAs(descriptor);
        assertThat(resolved.diagnostics()).isEmpty();
        assertThat(factory.descriptorCalls()).isEqualTo(1);
        assertThat(factory.validateCalls()).isEqualTo(1);
    }

    @Test
    void rejectsMissingProvider() {
        var failingConfig = config("orders", "missing", "1.0.0", Map.of());
        assertThatThrownBy(() -> resolver.resolve(failingConfig))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_NOT_FOUND);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_NOT_FOUND);
                });
    }

    @Test
    void rejectsVersionMismatch() {
        registry.add(new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema())));

        var failingConfig = config("orders", "orders", "9.9.9", Map.of("host", "localhost"));
        assertThatThrownBy(() -> resolver.resolve(failingConfig))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                });
    }

    @Test
    void rejectsSpiMajorMismatch() {
        registry.add(new FakeFactory(sourceDescriptor("orders", "1.0.0", 2, TestProviders.hostConfigSchema())));

        var failingConfig = config("orders", "orders", "1.0.0", Map.of("host", "localhost"));
        assertThatThrownBy(() -> resolver.resolve(failingConfig))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                });
    }

    @Test
    void resolvesDescriptorKindWithoutConfigurationKind() {
        ProviderDescriptor transformDescriptor = TestProviders.transform("orders", "1.0.0", 1, "in",
                TestProviders.schema("orders-in", "id", new FieldType.StringType()),
                "out", TestProviders.schema("orders-out", "id", new FieldType.StringType()),
                TestProviders.hostConfigSchema());
        registry.add(new FakeFactory(transformDescriptor));

        ProviderResolver.ResolvedProvider resolved = resolver.resolve(
                config("orders", "orders", "1.0.0", Map.of("host", "localhost")));

        assertThat(resolved.descriptor().capabilityKind()).isEqualTo(CapabilityKind.TRANSFORM);
    }

    @Test
    void warningsDoNotBlockResolution() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.deprecatedHostConfigSchema()),
                List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.WARNING, "/config",
                        "provider warning", null)));
        registry.add(factory);
        ProviderDescriptor descriptor = registry.registration(new ProviderId("orders")).orElseThrow().descriptor();

        ProviderResolver.ResolvedProvider resolved = resolver.resolve(
                config("orders", "orders", "1.0.0",
                        Map.of("host", "localhost", "old-host", "legacy")));

        assertThat(resolved.factory()).isSameAs(factory);
        assertThat(resolved.descriptor()).isSameAs(descriptor);
        assertThat(resolved.diagnostics()).extracting(Diagnostic::message)
                .contains("configuration field is deprecated", "provider warning");
    }

    @Test
    void warningsArePreservedWithProviderErrors() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.deprecatedHostConfigSchema()),
                List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, "/config",
                        "provider error", null)));
        registry.add(factory);

        var config = config("orders", "orders", "1.0.0",
                Map.of("host", "localhost", "old-host", "legacy"));
        assertThatThrownBy(() -> resolver.resolve(config))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .contains("configuration field is deprecated", "provider error");
                });
    }
}
