package dev.hogwai.platform.spi.provider;

import java.util.List;
import java.util.Map;

import dev.hogwai.platform.spi.Diagnostic;

/**
 * Provider factory for capabilities whose results stream instead of
 * materializing.
 *
 * <p>Registered and discovered exactly like any provider factory through
 * {@code ServiceLoader}, under its own service contract.
 */
public interface StreamingProviderFactory {

    /**
     * Returns the static descriptor of the provider capability.
     *
     * @return the provider descriptor
     */
    ProviderDescriptor descriptor();

    /**
     * Validates the raw configuration against the provider's own rules.
     * Implementations must perform this validation deterministically and
     * without side effects.
     *
     * @param rawConfig the raw configuration after generic validation
     * @return an immutable list of diagnostics; empty when valid
     */
    List<Diagnostic> validate(Map<String, Object> rawConfig);

    /**
     * Creates a streaming instance from the raw configuration.
     *
     * @param rawConfig the raw configuration after generic validation
     * @param context   the build context
     * @return the streaming instance
     */
    StreamingInstance create(Map<String, Object> rawConfig, BuildContext context);
}
