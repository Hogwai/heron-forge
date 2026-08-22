package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ServiceLoaderProviderRegistry} discovery and loading.
 *
 * <p>Proves that fixture providers are discovered through the Java
 * {@link java.util.ServiceLoader} mechanism from the test classpath without
 * modifying the runtime, that each instance loads the service exactly once and
 * reads each factory descriptor exactly once, and that the registry keeps the
 * descriptor it observed.
 */
class ServiceLoaderProviderRegistryTest {

    @Test
    void loadsProvidersFromServiceLoader() {
        ServiceLoaderProviderRegistry registry = new ServiceLoaderProviderRegistry();

        assertThat(registry.registration(new ProviderId("orders"))).isPresent();
    }

    @Test
    void exposesImmutableDescriptors() {
        ServiceLoaderProviderRegistry registry = new ServiceLoaderProviderRegistry();

        ProviderDescriptor descriptor = registry.registration(new ProviderId("orders")).orElseThrow().descriptor();
        assertThatThrownBy(() -> descriptor.inputPorts().put(new PortId("x"), null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> descriptor.outputPorts().put(new PortId("x"), null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registrationIsAtomic() {
        ServiceLoaderProviderRegistry registry = new ServiceLoaderProviderRegistry();

        ProviderRegistry.Registration registration = registry.registration(new ProviderId("orders")).orElseThrow();
        assertThat(registration.factory()).isNotNull();
        assertThat(registration.descriptor()).isNotNull();
        assertThat(registration.descriptor().providerId()).isEqualTo(new ProviderId("orders"));
    }

    @Test
    void readsDescriptorExactlyOncePerFactory(@TempDir Path tempDir) throws Exception {
        CountingDescriptorProviderFactory.reset();
        writeServiceFile(tempDir, "dev.hogwai.platform.runtime.compile.provider.CountingDescriptorProviderFactory\n");

        try (URLClassLoader loader = loader(tempDir)) {
            ServiceLoaderProviderRegistry registry = new ServiceLoaderProviderRegistry(loader);
            assertThat(CountingDescriptorProviderFactory.descriptorCalls()).isEqualTo(1);

            ProviderDescriptor stored = registry.registration(new ProviderId("counting")).orElseThrow().descriptor();
            assertThat(stored).isSameAs(CountingDescriptorProviderFactory.firstDescriptor());

            registry.registration(new ProviderId("counting"));
            assertThat(CountingDescriptorProviderFactory.descriptorCalls()).isEqualTo(1);
            assertThat(registry.registration(new ProviderId("counting")).orElseThrow().descriptor())
                    .isSameAs(stored);
        }
    }

    @Test
    void discoversServiceResourceOncePerInstance() {
        TestServiceClassLoader loader = new TestServiceClassLoader(getClass().getClassLoader());

        new ServiceLoaderProviderRegistry(loader);
        assertThat(loader.serviceResourceEnumerations()).isEqualTo(1);

        new ServiceLoaderProviderRegistry(loader);
        assertThat(loader.serviceResourceEnumerations()).isEqualTo(2);
    }

    private static void writeServiceFile(Path tempDir, String content) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        Files.writeString(servicesDir.resolve("dev.hogwai.platform.spi.provider.ProviderFactory"), content);
    }

    private static URLClassLoader loader(Path tempDir) throws IOException {
        return new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, ServiceLoaderProviderRegistryTest.class.getClassLoader());
    }
}
