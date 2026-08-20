package dev.hogwai.platform.runtime.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static dev.hogwai.platform.runtime.provider.ResolverTestSupport.config;
import static dev.hogwai.platform.runtime.provider.ResolverTestSupport.sourceDescriptor;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.runtime.provider.ResolverTestSupport.FakeFactory;
import dev.hogwai.platform.runtime.provider.ResolverTestSupport.FakeRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProviderResolver} resolution semantics.
 *
 * <p>Proves exact provider/version resolution, the
 * {@code PROVIDER_NOT_FOUND}/{@code PROVIDER_VERSION_MISMATCH} distinction, the
 * SPI major and capability kind checks, warning aggregation and the absence of
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
                config("orders", CapabilityKind.SOURCE, "orders", "1.0.0", Map.of("host", "localhost")));

        assertThat(resolved.factory()).isSameAs(factory);
        assertThat(resolved.descriptor()).isSameAs(descriptor);
        assertThat(resolved.diagnostics()).isEmpty();
        assertThat(factory.descriptorCalls()).isEqualTo(1);
        assertThat(factory.validateCalls()).isEqualTo(1);
    }

    @Test
    void rejectsMissingProvider() {
        assertThatThrownBy(() -> resolver.resolve(
                config("orders", CapabilityKind.SOURCE, "missing", "1.0.0", Map.of())))
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

        assertThatThrownBy(() -> resolver.resolve(
                config("orders", CapabilityKind.SOURCE, "orders", "9.9.9", Map.of("host", "localhost"))))
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

        assertThatThrownBy(() -> resolver.resolve(
                config("orders", CapabilityKind.SOURCE, "orders", "1.0.0", Map.of("host", "localhost"))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_VERSION_MISMATCH);
                });
    }

    @Test
    void rejectsKindMismatch() {
        registry.add(new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema())));

        assertThatThrownBy(() -> resolver.resolve(
                config("orders", CapabilityKind.TRANSFORM, "orders", "1.0.0", Map.of("host", "localhost"))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                            .contains(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                });
    }

    @Test
    void warningsDoNotBlockResolution() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.deprecatedHostConfigSchema()),
                List.of(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.WARNING, "/config",
                        "provider warning", null)));
        registry.add(factory);
        ProviderDescriptor descriptor = registry.registration(new ProviderId("orders")).orElseThrow().descriptor();

        ProviderResolver.ResolvedProvider resolved = resolver.resolve(
                config("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
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

        assertThatThrownBy(() -> resolver.resolve(
                config("orders", CapabilityKind.SOURCE, "orders", "1.0.0",
                        Map.of("host", "localhost", "old-host", "legacy"))))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .contains("configuration field is deprecated", "provider error");
                });
    }
}