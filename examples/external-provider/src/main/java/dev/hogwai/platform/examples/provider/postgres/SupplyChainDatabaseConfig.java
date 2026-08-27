package dev.hogwai.platform.examples.provider.postgres;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PostgreSQL configuration shared by the source providers.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class SupplyChainDatabaseConfig {
    private SupplyChainDatabaseConfig() {
        /* This utility class should not be instantiated */
    }

    public static final String PASSWORD = "password";
    public static final String MAX_ROWS = "maxRows";
    public static final String MAX_BYTES = "maxBytes";
    private static final Set<String> DATABASE_FIELDS = Set.of("url", "user", PASSWORD, MAX_ROWS, MAX_BYTES);
    private static final List<String> REQUIRED_FIELDS = List.of("url", "user", PASSWORD);
    private static final ConfigurationSchema SCHEMA = new ConfigurationSchema(DATABASE_FIELDS, Set.of(),
            Map.of("url", ConfigurationSchema.ScalarKind.STRING,
                    "user", ConfigurationSchema.ScalarKind.STRING,
                    PASSWORD, ConfigurationSchema.ScalarKind.STRING,
                    MAX_ROWS, ConfigurationSchema.ScalarKind.INTEGER,
                    MAX_BYTES, ConfigurationSchema.ScalarKind.INTEGER), Map.of());

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
            String key = entry.getKey();
            if (!DATABASE_FIELDS.contains(key)) {
                diagnostics.add(databaseDiagnostic("/config/<key>", "unknown database configuration field"));
            } else if (key.equals(MAX_ROWS) || key.equals(MAX_BYTES)) {
                if (!(entry.getValue() instanceof Number value) || value.longValue() <= 0) {
                    diagnostics.add(databaseDiagnostic("/config/" + key,
                            "dataset limit must be a positive integer"));
                }
            } else if (!(entry.getValue() instanceof String value) || value.isBlank()) {
                diagnostics.add(databaseDiagnostic("/config/" + key,
                        "database configuration field must be a non-blank string"));
            }
        }
        for (String field : REQUIRED_FIELDS) {
            if (rawConfig.get(field) == null) {
                diagnostics.add(databaseDiagnostic("/config/" + field,
                        "missing required database configuration field"));
            }
        }
        return List.copyOf(diagnostics);
    }

    public static DataAccessConfiguration from(Map<String, Object> rawConfig) {
        Map<String, Object> config = rawConfig == null ? Map.of() : rawConfig;
        return new DataAccessConfiguration(
                requiredString(config, "url"),
                requiredString(config, "user"),
                requiredString(config, PASSWORD));
    }

    /**
     * Returns the dataset limits from the configuration, or the defaults.
     */
    public static DataSetLimits limits(Map<String, Object> rawConfig) {
        Map<String, Object> config = rawConfig == null ? Map.of() : rawConfig;
        return new DataSetLimits(longValue(config, MAX_ROWS, 1_000), longValue(config, MAX_BYTES, 1_000_000));
    }

    private static long longValue(Map<String, Object> config, String field, long fallback) {
        Object configured = config.get(field);
        if (configured instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        return fallback;
    }

    private static String requiredString(Map<String, Object> config, String field) {
        Object configured = config.get(field);
        if (configured instanceof String string && !string.isBlank()) {
            return string;
        }
        // Unreachable through create(): validate() rejects a missing or blank
        // password before the configuration is decoded.
        throw new IllegalArgumentException("missing required database configuration field: " + field);
    }

    private static Diagnostic databaseDiagnostic(String path, String message) {
        return new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, path, message,
                "check the PostgreSQL configuration");
    }
}
