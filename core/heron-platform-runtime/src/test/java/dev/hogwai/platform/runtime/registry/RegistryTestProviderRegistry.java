package dev.hogwai.platform.runtime.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.spi.ProviderId;

/** In-memory provider registry fixture for the registry tests. */
final class RegistryTestProviderRegistry implements ProviderRegistry {

    private final Map<ProviderId, Registration> registrations = new LinkedHashMap<>();

    void add(RegistryTestSourceFactory factory) {
        registrations.put(factory.descriptor().providerId(), new Registration(factory, factory.descriptor()));
    }

    @Override
    public Optional<Registration> registration(ProviderId providerId) {
        return Optional.ofNullable(registrations.get(providerId));
    }
}
