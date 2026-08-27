package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
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
 * Tests for {@link ServiceLoaderProviderRegistry} failure normalization.
 *
 * <p>Proves that incoherent, duplicated or failing providers are rejected with
 * {@link PlatformErrorCode#PROVIDER_CONFIG_ERROR} diagnostics that never leak
 * classpath, exception or sensitive details and always carry the safe service
 * name {@code dev.hogwai.platform.spi.provider.ProviderFactory}.
 */
class ServiceLoaderProviderRegistryFailureTest {

    @Test
    void rejectsDuplicateProviderIds(@TempDir Path tempDir) throws Exception {
        writeServiceFile(tempDir, """
                dev.hogwai.platform.runtime.compile.provider.OrdersProviderFactory
                dev.hogwai.platform.runtime.compile.provider.DuplicateOrdersProviderFactory
                """);

        try (URLClassLoader loader = loader(tempDir)) {
            assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                    .isInstanceOf(PlatformException.class)
                    .satisfies(e -> {
                        PlatformException pe = (PlatformException) e;
                        assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                        assertThat(pe.diagnostics()).extracting(Diagnostic::code)
                                .contains(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .anyMatch(m -> m.contains("duplicate provider id"));
                    });
        }
    }

    @Test
    void rejectsNullDescriptor(@TempDir Path tempDir) throws Exception {
        writeServiceFile(tempDir, "dev.hogwai.platform.runtime.compile.provider.NullDescriptorProviderFactory\n");

        try (URLClassLoader loader = loader(tempDir)) {
            assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                    .isInstanceOf(PlatformException.class)
                    .satisfies(e -> {
                        PlatformException pe = (PlatformException) e;
                        assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .anyMatch(m -> m.contains("null descriptor"));
                    });
        }
    }

    @Test
    void rejectsThrowingDescriptor(@TempDir Path tempDir) throws Exception {
        writeServiceFile(tempDir, "dev.hogwai.platform.runtime.compile.provider.ThrowingDescriptorProviderFactory\n");

        try (URLClassLoader loader = loader(tempDir)) {
            assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                    .isInstanceOf(PlatformException.class)
                    .satisfies(e -> {
                        PlatformException pe = (PlatformException) e;
                        assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .anyMatch(m -> m.contains("failed to provide a descriptor"));
                    });
        }
    }

    @Test
    void rejectsMissingServiceClassWithSafeServiceName(@TempDir Path tempDir) throws Exception {
        writeServiceFile(tempDir, "dev.hogwai.platform.runtime.compile.provider.DoesNotExistProviderFactory\n");

        try (URLClassLoader loader = loader(tempDir)) {
            assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                    .isInstanceOf(PlatformException.class)
                    .satisfies(e -> {
                        PlatformException pe = (PlatformException) e;
                        assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .anyMatch(m -> m.contains("dev.hogwai.platform.spi.provider.ProviderFactory"));
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .noneMatch(m -> m.contains("DoesNotExist"));
                    });
        }
    }

    @Test
    void rejectsThrowingProviderConstructorWithSafeServiceName(@TempDir Path tempDir) throws Exception {
        writeServiceFile(tempDir, "dev.hogwai.platform.runtime.compile.provider.ThrowingConstructorProviderFactory\n");

        try (URLClassLoader loader = loader(tempDir)) {
            assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                    .isInstanceOf(PlatformException.class)
                    .satisfies(e -> {
                        PlatformException pe = (PlatformException) e;
                        assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .anyMatch(m -> m.contains("dev.hogwai.platform.spi.provider.ProviderFactory"));
                        assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                                .noneMatch(m -> m.contains("cannot construct"));
                    });
        }
    }

    @Test
    void rejectsSecurityExceptionWithSafeServiceName() {
        TestServiceClassLoader loader = new TestServiceClassLoader(getClass().getClassLoader());
        loader.failWithSecurity();

        assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("dev.hogwai.platform.spi.provider.ProviderFactory"));
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .noneMatch(m -> m.contains("access denied"));
                });
    }

    @Test
    void rejectsLinkageErrorWithSafeServiceName() {
        TestServiceClassLoader loader = new TestServiceClassLoader(getClass().getClassLoader());
        loader.failWithLinkage();

        assertThatThrownBy(() -> new ServiceLoaderProviderRegistry(loader))
                .isInstanceOf(PlatformException.class)
                .satisfies(e -> {
                    PlatformException pe = (PlatformException) e;
                    assertThat(pe.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .anyMatch(m -> m.contains("dev.hogwai.platform.spi.provider.ProviderFactory"));
                    assertThat(pe.diagnostics()).extracting(Diagnostic::message)
                            .noneMatch(m -> m.contains("missing dependency"));
                });
    }

    private static void writeServiceFile(Path tempDir, String content) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        Files.writeString(servicesDir.resolve("dev.hogwai.platform.spi.provider.ProviderFactory"), content);
    }

    private static URLClassLoader loader(Path tempDir) throws IOException {
        return new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
                ServiceLoaderProviderRegistryFailureTest.class.getClassLoader());
    }
}