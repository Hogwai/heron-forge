package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import java.util.Objects;

/**
 * Immutable context passed to {@link ProviderFactory#validate}.
 *
 * <p>It exposes only the provider identity and version being validated. It
 * carries no raw configuration, secrets or business payload. Framework-independent.
 */
@SuppressWarnings("java:S6206")
public final class ValidationContext {

    private final ProviderId providerId;
    private final ProviderVersion version;

    /**
     * Creates a validation context.
     *
     * @param providerId the provider identifier
     * @param version    the provider version
     * @throws NullPointerException if {@code providerId} or {@code version} is {@code null}
     */
    public ValidationContext(ProviderId providerId, ProviderVersion version) {
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.version = Objects.requireNonNull(version, "version must not be null");
    }

    /**
     * Returns the provider identifier.
     *
     * @return the provider identifier
     */
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider version.
     *
     * @return the provider version
     */
    public ProviderVersion version() {
        return version;
    }
}
