package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.Diagnostic;
import java.util.List;
import java.util.Map;

/**
 * Factory that describes, validates and instantiates a provider capability.
 *
 * <p>The {@code rawConfig} passed to {@link #validate} and {@link #create} is
 * the raw configuration after generic validation; the provider decodes it
 * itself. The collections exposed or returned by this contract must not be
 * mutated by callers. Framework-independent.
 */
public interface ProviderFactory {

    /**
     * Returns the static descriptor of the provider capability.
     *
     * @return the provider descriptor
     */
    ProviderDescriptor descriptor();

    /**
     * Validates the raw configuration against the provider's own rules.
     * Implementations must perform this validation deterministically and
     * without side effects: they must not use the network, acquire resources,
     * mutate global state, or depend on a running host.
     *
     * @param rawConfig the raw configuration after generic validation
     * @return an immutable list of diagnostics; empty when valid
     */
    List<Diagnostic> validate(Map<String, Object> rawConfig);

    /**
     * Creates a capability instance from the raw configuration.
     *
     * @param rawConfig the raw configuration after generic validation
     * @param context   the build context
     * @return the capability instance
     */
    CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context);
}
