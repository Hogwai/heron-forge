package dev.hogwai.platform.runtime.compile.provider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

import static dev.hogwai.platform.spi.error.PlatformErrorCode.PROVIDER_CONFIG_ERROR;

/**
 * Resolves a {@link CapabilityConfig} to the exact provider factory and
 * descriptor.
 *
 * <p>Resolution matches the {@link ProviderId} and the exact
 * {@link ProviderVersion} declared by the configuration. A missing provider id
 * yields {@link PlatformErrorCode#PROVIDER_NOT_FOUND}; a present id with a
 * missing or mismatched version yields
 * {@link PlatformErrorCode#PROVIDER_VERSION_MISMATCH}. The descriptor's SPI
 * major version is then checked with coherent public diagnostics. The
 * descriptor's
 * {@link ConfigurationSchema} is validated generically before the provider's own
 * {@link ProviderFactory#validate} is invoked; when the generic validation
 * produces at least one error the provider's {@code validate} is not invoked.
 * All diagnostics are aggregated and any error severity diagnostic aborts
 * resolution. On success the aggregated warnings (generic and provider) are
 * preserved in the {@link ResolvedProvider}. No
 * {@link dev.hogwai.platform.spi.provider.CapabilityInstance} is created here
 * and no raw configuration is exposed by the public result.
 */
public final class ProviderResolver {

    // Diagnostic pointer paths, not network URIs: Sonar S1075 is intentionally
    // suppressed because these values must stay stable API-facing strings.
    @SuppressWarnings("java:S1075")
    private static final String CONFIG_PATH_PREFIX = "/config/";
    @SuppressWarnings("java:S1075")
    private static final String PROVIDER_ID_PATH = "/provider/id";
    @SuppressWarnings("java:S1075")
    private static final String PROVIDER_VERSION_PATH = "/provider/version";

    private final ProviderRegistry registry;

    /**
     * Creates a resolver backed by the given registry.
     *
     * @param registry the provider registry
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public ProviderResolver(ProviderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Resolves the provider for the given capability configuration.
     *
     * @param config the capability configuration
     * @return the resolved provider
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws PlatformException    if the provider is missing, the version or
     *                              SPI major does not match, or validation
     *                              produced error diagnostics
     */
    public ResolvedProvider resolve(CapabilityConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        ProviderId providerId = IdParser.parseProviderId(config.providerId());
        ProviderVersion version = IdParser.parseVersion(config.providerVersion());

        Optional<ProviderRegistry.Registration> registrationOpt = registry.registration(providerId);
        if (registrationOpt.isEmpty()) {
            throw new PlatformException(PlatformErrorCode.PROVIDER_NOT_FOUND, List.of(
                    new Diagnostic(PlatformErrorCode.PROVIDER_NOT_FOUND, Severity.ERROR, PROVIDER_ID_PATH,
                            "provider not found", "register the provider on the classpath")));
        }
        ProviderRegistry.Registration registration = registrationOpt.get();
        ProviderFactory factory = registration.factory();
        ProviderDescriptor descriptor = getProviderDescriptor(registration, version);
        List<Diagnostic> genericDiagnostics = GenericConfigChecks.validate(descriptor.configurationSchema(), config.config());
        if (genericDiagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR)) {
            throw new PlatformException(PROVIDER_CONFIG_ERROR, genericDiagnostics);
        }

        List<Diagnostic> providerDiagnostics = ProviderValidator.validate(factory, config.config());

        List<Diagnostic> diagnostics = new ArrayList<>(genericDiagnostics.size() + providerDiagnostics.size());
        diagnostics.addAll(genericDiagnostics);
        diagnostics.addAll(providerDiagnostics);

        if (diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR)) {
            throw new PlatformException(PROVIDER_CONFIG_ERROR, diagnostics);
        }
        return new ResolvedProvider(factory, descriptor, diagnostics);
    }

    private static ProviderDescriptor getProviderDescriptor(ProviderRegistry.Registration registration,
                                                            ProviderVersion version) {
        ProviderDescriptor descriptor = registration.descriptor();

        if (!descriptor.version().equals(version)) {
            throw new PlatformException(PlatformErrorCode.PROVIDER_VERSION_MISMATCH, List.of(
                    new Diagnostic(PlatformErrorCode.PROVIDER_VERSION_MISMATCH, Severity.ERROR, PROVIDER_VERSION_PATH,
                            "provider version does not match the required version", "use the exact provider version")));
        }
        if (descriptor.spiMajor() != SpiMajor.V1) {
            throw new PlatformException(PlatformErrorCode.PROVIDER_VERSION_MISMATCH, List.of(
                    new Diagnostic(PlatformErrorCode.PROVIDER_VERSION_MISMATCH, Severity.ERROR, PROVIDER_VERSION_PATH,
                            "provider SPI major version is not supported", "use a provider with SPI major " + SpiMajor.V1)));
        }
        return descriptor;
    }

    /**
     * Immutable result of a successful provider resolution.
     *
     * <p>Exposes the resolved {@link ProviderFactory}, {@link ProviderDescriptor}
     * and the immutable list of aggregated diagnostics (warnings preserved from
     * generic and provider validation). No raw configuration is carried.
     *
     * @param factory     the resolved provider factory
     * @param descriptor  the resolved provider descriptor
     * @param diagnostics the immutable aggregated diagnostics (warnings)
     */
    public record ResolvedProvider(ProviderFactory factory, ProviderDescriptor descriptor, List<Diagnostic> diagnostics) {
        /**
         * Compact constructor enforcing non-null components and an immutable
         * diagnostics list.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public ResolvedProvider {
            Objects.requireNonNull(factory, "factory must not be null");
            Objects.requireNonNull(descriptor, "descriptor must not be null");
            Objects.requireNonNull(diagnostics, "diagnostics must not be null");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /**
     * Private nested parser for provider identity and version strings.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class IdParser {

        private IdParser() {
            // no instances
        }

        static ProviderId parseProviderId(String value) {
            try {
                return new ProviderId(value);
            } catch (RuntimeException _) {
                throw new PlatformException(PROVIDER_CONFIG_ERROR, List.of(
                        new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR, PROVIDER_ID_PATH,
                                "provider id is invalid", "provide a valid provider id")));
            }
        }

        static ProviderVersion parseVersion(String value) {
            try {
                return ProviderVersion.parse(value);
            } catch (RuntimeException _) {
                throw new PlatformException(PROVIDER_CONFIG_ERROR, List.of(
                        new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR, PROVIDER_VERSION_PATH,
                                "provider version is invalid", "provide a canonical major.minor.patch version")));
            }
        }
    }

    /**
     * Private nested validator for the generic {@link ConfigurationSchema}
     * contract.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class GenericConfigChecks {

        private GenericConfigChecks() {
            // no instances
        }

        static List<Diagnostic> validate(ConfigurationSchema schema, Map<String, Object> rawConfig) {
            List<Diagnostic> diagnostics = new ArrayList<>();
            FieldValidator.validate(schema, rawConfig, diagnostics);
            KindValidator.validate(schema, rawConfig, diagnostics);
            return diagnostics;
        }
    }

    /**
     * Private nested validator for unknown and required configuration fields.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class FieldValidator {

        private FieldValidator() {
            // no instances
        }

        static void validate(ConfigurationSchema schema, Map<String, Object> rawConfig,
                             List<Diagnostic> diagnostics) {
            validateUnknownFields(schema, rawConfig, diagnostics);
            validateRequiredFields(schema, rawConfig, diagnostics);
        }

        private static void validateUnknownFields(ConfigurationSchema schema,
                                                  Map<String, Object> rawConfig,
                                                  List<Diagnostic> diagnostics) {
            for (String key : rawConfig.keySet()) {
                if (!schema.allowedFields().contains(key)) {
                    diagnostics.add(new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR, "%s%s".formatted(CONFIG_PATH_PREFIX, "<key>"),
                            "unknown configuration field", "remove the field or check the provider documentation"));
                }
            }
        }

        private static void validateRequiredFields(ConfigurationSchema schema,
                                                   Map<String, Object> rawConfig,
                                                   List<Diagnostic> diagnostics) {
            for (String required : schema.requiredFields()) {
                if (!rawConfig.containsKey(required)) {
                    diagnostics.add(new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR, CONFIG_PATH_PREFIX + required,
                            "missing required configuration field", "provide the required field"));
                }
            }
        }
    }

    /**
     * Private nested validator for configuration field kinds and deprecations.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class KindValidator {

        private KindValidator() {
            // no instances
        }

        static void validate(ConfigurationSchema schema, Map<String, Object> rawConfig,
                             List<Diagnostic> diagnostics) {
            validateFieldKinds(schema, rawConfig, diagnostics);
            validateDeprecations(schema, rawConfig, diagnostics);
        }

        private static void validateFieldKinds(ConfigurationSchema schema, Map<String, Object> rawConfig,
                                               List<Diagnostic> diagnostics) {
            for (Map.Entry<String, ConfigurationSchema.ScalarKind> entry : schema.fieldKinds().entrySet()) {
                Object value = rawConfig.get(entry.getKey());
                if (value != null && !matches(entry.getValue(), value)) {
                    diagnostics.add(new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR,
                            CONFIG_PATH_PREFIX + entry.getKey(), "configuration field has the wrong type",
                            "provide a value of the declared type"));
                }
            }
        }

        private static void validateDeprecations(ConfigurationSchema schema, Map<String, Object> rawConfig,
                                                 List<Diagnostic> diagnostics) {
            for (Map.Entry<String, String> entry : schema.deprecations().entrySet()) {
                if (rawConfig.containsKey(entry.getKey())) {
                    diagnostics.add(new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.WARNING,
                            CONFIG_PATH_PREFIX + entry.getKey(), "configuration field is deprecated", entry.getValue()));
                }
            }
        }

        private static boolean matches(ConfigurationSchema.ScalarKind kind, Object value) {
            return switch (kind) {
                case STRING -> value instanceof String;
                case BOOLEAN -> value instanceof Boolean;
                case INTEGER -> value instanceof Long;
                case NUMBER -> value instanceof BigDecimal;
            };
        }
    }

    /**
     * Private nested validator that invokes the provider's own
     * {@link ProviderFactory#validate} exactly once and converts any failure
     * (exception, null list or null element) into a coherent
     * {@code PROVIDER_CONFIG_ERROR} diagnostic without leaking details.
     *
     * <p>Kept as a private nested helper so that the public class stays within
     * the project's cyclomatic complexity budget.
     */
    private static final class ProviderValidator {

        private static final String CHECK_PROVIDER_IMPLEMENTATION = "check the provider implementation";

        private ProviderValidator() {
            // no instances
        }

        static List<Diagnostic> validate(ProviderFactory factory, Map<String, Object> rawConfig) {
            List<Diagnostic> result;
            try {
                result = factory.validate(rawConfig);
            } catch (RuntimeException _) {
                return List.of(new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "provider validation failed", CHECK_PROVIDER_IMPLEMENTATION));
            }
            if (result == null) {
                return List.of(new Diagnostic(PROVIDER_CONFIG_ERROR, Severity.ERROR, null,
                        "provider validation returned no diagnostics", CHECK_PROVIDER_IMPLEMENTATION));
            }
            return createDiagnostics(result);
        }

        private static List<Diagnostic> createDiagnostics(List<Diagnostic> result) {
            List<Diagnostic> converted = new ArrayList<>(result.size());
            for (Diagnostic diagnostic : result) {
                converted.add(Objects.requireNonNullElseGet(
                        diagnostic, () -> new Diagnostic(PROVIDER_CONFIG_ERROR,
                                Severity.ERROR,
                                null,
                                "provider validation returned a null diagnostic",
                                CHECK_PROVIDER_IMPLEMENTATION)));
            }
            return converted;
        }
    }
}
