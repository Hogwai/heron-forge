package dev.hogwai.platform.examples.provider.postgres;

import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PostgreSQL configuration shared by the source providers. */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class SupplyChainDatabaseConfig {

    private static final Set<String> DATABASE_FIELDS = Set.of("url", "user", "password");
    private static final ConfigurationSchema SCHEMA = new ConfigurationSchema(DATABASE_FIELDS, Set.of(),
            Map.of("url", ConfigurationSchema.ScalarKind.STRING,
                    "user", ConfigurationSchema.ScalarKind.STRING,
                    "password", ConfigurationSchema.ScalarKind.STRING), Map.of());

    public static ConfigurationSchema databaseConfigSchema() {
        return SCHEMA;
    }

    public static List<Diagnostic> validate(Map<String, Object> rawConfig,
            String providerName) {
        if (rawConfig == null) {
            return List.of(databaseDiagnostic("/config", providerName + " configuration must be an object"));
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawConfig.entrySet()) {
            if (!DATABASE_FIELDS.contains(entry.getKey())) {
                diagnostics.add(databaseDiagnostic("/config/<key>", "unknown database configuration field"));
            } else if (!(entry.getValue() instanceof String value) || value.isBlank()) {
                diagnostics.add(databaseDiagnostic("/config/" + entry.getKey(),
                        "database configuration field must be a non-blank string"));
            }
        }
        return List.copyOf(diagnostics);
    }

    public static DataAccessConfiguration from(Map<String, Object> rawConfig) {
        Map<String, Object> config = rawConfig == null ? Map.of() : rawConfig;
        return new DataAccessConfiguration(
                value(config, "url", "HERON_DB_URL", "jdbc:postgresql://localhost:5432/heron_demo"),
                value(config, "user", "HERON_DB_USER", "heron"),
                value(config, "password", "HERON_DB_PASSWORD", "heron"));
    }

    private static String value(Map<String, Object> config, String field, String environment, String fallback) {
        Object configured = config.get(field);
        if (configured instanceof String string && !string.isBlank()) {
            return string;
        }
        String fromEnvironment = System.getenv(environment);
        return fromEnvironment == null || fromEnvironment.isBlank() ? fallback : fromEnvironment;
    }

    private static Diagnostic databaseDiagnostic(String path, String message) {
        return new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, path, message,
                "check the PostgreSQL configuration");
    }
}
