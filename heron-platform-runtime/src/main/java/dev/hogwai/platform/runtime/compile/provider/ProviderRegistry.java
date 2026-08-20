package dev.hogwai.platform.runtime.compile.provider;

import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal, immutable abstraction over the set of available providers.
 *
 * <p>A registry exposes an atomic {@link Registration} (the {@link ProviderFactory}
 * and its immutable {@link ProviderDescriptor} observed together from a single
 * discovery pass) for each registered provider, keyed by {@link ProviderId}.
 * Implementations must be immutable after construction and must expose only
 * immutable views of their collections. Framework-independent.
 */
public interface ProviderRegistry {

    /**
     * Returns the atomic factory/descriptor registration for the given
     * provider, if present.
     *
     * @param providerId the provider identifier
     * @return the registration, or {@link Optional#empty()} if the provider is
     *         not registered
     * @throws NullPointerException if {@code providerId} is {@code null}
     */
    Optional<Registration> registration(ProviderId providerId);

    /**
     * Atomic, cohesive registration of a provider: the {@link ProviderFactory}
     * and its immutable {@link ProviderDescriptor} observed together from a
     * single discovery pass. No raw configuration is carried.
     *
     * @param factory    the provider factory
     * @param descriptor the provider descriptor
     */
    record Registration(ProviderFactory factory, ProviderDescriptor descriptor) {
        /**
         * Compact constructor enforcing non-null components.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public Registration {
            Objects.requireNonNull(factory, "factory must not be null");
            Objects.requireNonNull(descriptor, "descriptor must not be null");
        }
    }
}
