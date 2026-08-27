package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.FakeFactory;
import dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.FakeRegistry;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.config;
import static dev.hogwai.platform.runtime.compile.provider.ResolverTestSupport.sourceDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProviderResolver} validation semantics.
 *
 * <p>Proves that generic validation errors prevent the provider's own
 * {@code validate} from running, and that provider validation failures
 * (exception, null list, null element) are converted into coherent
 * {@code PROVIDER_CONFIG_ERROR} diagnostics without leaking details.
 */
class ProviderResolverValidationTest {

    private final FakeRegistry registry = new FakeRegistry();
    private final ProviderResolver resolver = new ProviderResolver(registry);

    @Test
    void genericMissingRequiredFieldPreventsProviderValidate() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()));
        registry.add(factory);
        var ordersConfig = config("orders", "orders", "1.0.0", Map.of());

        assertThatThrownBy(() -> resolver.resolve(ordersConfig))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("missing required configuration field"));
                });
        assertThat(factory.validateCalls()).isZero();
    }

    @Test
    void genericWrongTypePreventsProviderValidate() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()));
        registry.add(factory);
        var ordersConfig = config("orders", "orders", "1.0.0", Map.of("host", 42L));

        assertThatThrownBy(() -> resolver.resolve(ordersConfig))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("wrong type"));
                });
        assertThat(factory.validateCalls()).isZero();
    }

    @Test
    void genericUnknownFieldPreventsProviderValidate() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()));
        registry.add(factory);
        var ordersConfig = config("orders", "orders", "1.0.0",
                Map.of("host", "localhost", "unknown", "x"));

        assertThatThrownBy(() -> resolver.resolve(ordersConfig))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("unknown configuration field"));
                });
        assertThat(factory.validateCalls()).isZero();
    }

    @Test
    void providerValidateThrowIsConvertedWithoutLeak() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()),
                true, false, false);
        registry.add(factory);
        var config = config("orders", "orders", "1.0.0", Map.of("host", "localhost"));

        assertThatThrownBy(() -> resolver.resolve(config))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("provider validation failed"));
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .noneMatch(m -> m.contains("exploded"));
                });
        assertThat(factory.validateCalls()).isEqualTo(1);
    }

    @Test
    void providerValidateNullListIsConverted() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()),
                false, true, false);
        registry.add(factory);
        var config = config("orders", "orders", "1.0.0", Map.of("host", "localhost"));

        assertThatThrownBy(() -> resolver.resolve(config))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("provider validation returned no diagnostics"));
                });
    }

    @Test
    void providerValidateNullElementIsConvertedAndValidDiagnosticsKept() {
        FakeFactory factory = new FakeFactory(sourceDescriptor("orders", "1.0.0", TestProviders.hostConfigSchema()),
                false, false, true);
        registry.add(factory);
        var config = config("orders", "orders", "1.0.0", Map.of("host", "localhost"));

        assertThatThrownBy(() -> resolver.resolve(config))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .contains("provider warning", "provider error",
                                    "provider validation returned a null diagnostic");
                });
    }
}
