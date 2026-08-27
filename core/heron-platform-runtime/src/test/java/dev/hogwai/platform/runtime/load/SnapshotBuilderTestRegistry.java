package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.runtime.compile.provider.ProviderRegistry;
import dev.hogwai.platform.spi.ProviderId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory provider registry fixture.
 */
final class SnapshotBuilderTestRegistry implements ProviderRegistry {

    private final Map<ProviderId, Registration> registrations;

    SnapshotBuilderTestRegistry(Registration... registrations) {
        Map<ProviderId, Registration> map = new LinkedHashMap<>();
        for (Registration registration : registrations) {
            map.put(registration.descriptor().providerId(), registration);
        }
        this.registrations = map;
    }

    @Override
    public Optional<Registration> registration(ProviderId providerId) {
        return Optional.ofNullable(registrations.get(providerId));
    }

}
