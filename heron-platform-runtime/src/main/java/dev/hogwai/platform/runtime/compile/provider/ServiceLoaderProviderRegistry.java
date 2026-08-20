package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.PlatformException;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * {@link ProviderRegistry} implementation that discovers providers through the
 * Java {@link ServiceLoader} mechanism on the trusted classpath.
 *
 * <p>Providers are loaded exactly once per instance from the runtime
 * classloader. No dynamic classloader, jar installation, sandbox, hot install
 * or multi-version selection is performed: only the trusted classpath is
 * consulted. A provider factory whose descriptor is {@code null}, incoherent or
 * duplicated is rejected, and any service loading failure is reported as a
 * {@link PlatformException} with {@link PlatformErrorCode#PROVIDER_CONFIG_ERROR}
 * diagnostics that never leak raw classpath or exception details.
 */
public final class ServiceLoaderProviderRegistry implements ProviderRegistry {

    private final Map<ProviderId, Registration> registrations;

    /**
     * Creates a registry that loads providers from the runtime classloader.
     *
     * @throws PlatformException with {@code PROVIDER_CONFIG_ERROR} if any
     *                           provider cannot be loaded or is incoherent
     */
    public ServiceLoaderProviderRegistry() {
        this(ServiceLoaderProviderRegistry.class.getClassLoader());
    }

    /**
     * Creates a registry that loads providers from the given classloader.
     *
     * <p>Package-private on purpose: only the runtime no-arg constructor is a
     * public API. Tests inside the provider package may inject a classloader to
     * exercise discovery failures without exposing a dynamic classloader API.
     *
     * @param classLoader the trusted classloader to load providers from
     * @throws NullPointerException if {@code classLoader} is {@code null}
     * @throws PlatformException    with {@code PROVIDER_CONFIG_ERROR} if any
     *                              provider cannot be loaded or is incoherent
     */
    ServiceLoaderProviderRegistry(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        this.registrations = Loader.load(classLoader);
    }

    @Override
    public Optional<Registration> registration(ProviderId providerId) {
        return Optional.ofNullable(registrations.get(Objects.requireNonNull(providerId, "providerId must not be null")));
    }

    /**
     * Private nested loader that performs the ServiceLoader iteration.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class Loader {

        private Loader() {
            // no instances
        }

        static Map<ProviderId, Registration> load(ClassLoader classLoader) {
            Map<ProviderId, Registration> registrations = new LinkedHashMap<>();
            List<Diagnostic> diagnostics = new ArrayList<>();
            try {
                ServiceLoader<ProviderFactory> loader = ServiceLoader.load(ProviderFactory.class, classLoader);
                for (ProviderFactory factory : loader) {
                    Registrar.register(factory, registrations, diagnostics);
                }
            } catch (ServiceConfigurationError | SecurityException | LinkageError _) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "provider service dev.hogwai.platform.spi.provider.ProviderFactory could not be loaded",
                        "check the provider implementation and its service registration"));
            }
            if (!diagnostics.isEmpty()) {
                throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, diagnostics);
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(registrations));
        }
    }

    /**
     * Private nested registrar that validates and registers a single factory.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class Registrar {

        private Registrar() {
            // no instances
        }

        static void register(ProviderFactory factory, Map<ProviderId, Registration> registrations,
                             List<Diagnostic> diagnostics) {
            ProviderDescriptor descriptor;
            try {
                descriptor = factory.descriptor();
            } catch (RuntimeException _) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "provider factory failed to provide a descriptor", "check the provider implementation"));
                return;
            }
            if (descriptor == null) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "provider factory returned a null descriptor", "check the provider implementation"));
                return;
            }
            ProviderId providerId = descriptor.providerId();
            if (providerId == null) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "provider descriptor has a null provider id", "check the provider implementation"));
                return;
            }
            if (registrations.containsKey(providerId)) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "duplicate provider id", "use a unique provider id"));
                return;
            }
            registrations.put(providerId, new Registration(factory, descriptor));
        }
    }
}
