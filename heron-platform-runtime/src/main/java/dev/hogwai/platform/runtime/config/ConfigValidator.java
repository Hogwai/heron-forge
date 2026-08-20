package dev.hogwai.platform.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.mapping.ConfigMapper;

/**
 * Validates the shape of a parsed application configuration and maps it to an
 * immutable {@link ApplicationConfig}.
 *
 * <p>Enforces the exact root shape ({@code apiVersion}, {@code kind},
 * {@code metadata}, {@code spec}), the required capability members, the input
 * binding shape, unknown-field rejection and duplicate capability id
 * detection. Violations are reported as {@code CONFIG_SCHEMA_ERROR}
 * diagnostics with precise YAML paths and non-empty remediation.
 */
public final class ConfigValidator {

    /**
     * Validates a parsed root node and maps it to an application configuration.
     *
     * @param root the parsed root node
     * @return the parsed application with any schema diagnostics
     */
    public ParsedApplication validate(JsonNode root) {
        return ConfigMapper.mapApplication(root);
    }
}
