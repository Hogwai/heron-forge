package dev.hogwai.platform.examples.provider.exceptions;

import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PlatformErrorCode;
import dev.hogwai.platform.spi.Severity;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable thresholds used by the supply-chain exception detector. */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class DetectorConfig {

    static final String LATE_TOLERANCE = "lateToleranceDays";
    static final String DELIVERY_RATIO = "minimumDeliveryRatio";
    static final String PRIORITY_RISK = "priorityRiskDays";

    private static final ConfigurationSchema SCHEMA = new ConfigurationSchema(
            Set.of(LATE_TOLERANCE, DELIVERY_RATIO, PRIORITY_RISK),
            Set.of(LATE_TOLERANCE, DELIVERY_RATIO, PRIORITY_RISK),
            Map.of(LATE_TOLERANCE, ConfigurationSchema.ScalarKind.INTEGER,
                    DELIVERY_RATIO, ConfigurationSchema.ScalarKind.NUMBER,
                    PRIORITY_RISK, ConfigurationSchema.ScalarKind.INTEGER), Map.of());

    private final long lateToleranceDays;
    private final BigDecimal minimumDeliveryRatio;
    private final long priorityRiskDays;

    private DetectorConfig(long lateToleranceDays, BigDecimal minimumDeliveryRatio, long priorityRiskDays) {
        this.lateToleranceDays = lateToleranceDays;
        this.minimumDeliveryRatio = minimumDeliveryRatio;
        this.priorityRiskDays = priorityRiskDays;
    }

    static ConfigurationSchema configurationSchema() {
        return SCHEMA;
    }

    static List<Diagnostic> validate(Map<String, Object> rawConfig) {
        if (rawConfig == null) {
            return List.of(error("/config", "detector configuration is required"));
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        validateLong(rawConfig, LATE_TOLERANCE, diagnostics, value -> value >= 0);
        validateRatio(rawConfig, diagnostics);
        validateLong(rawConfig, PRIORITY_RISK, diagnostics, value -> value >= 0);
        return List.copyOf(diagnostics);
    }

    static DetectorConfig from(Map<String, Object> rawConfig) {
        return new DetectorConfig(
                numberAsLong(rawConfig.get(LATE_TOLERANCE)),
                numberAsDecimal((Number) rawConfig.get(DELIVERY_RATIO)),
                numberAsLong(rawConfig.get(PRIORITY_RISK)));
    }

    long lateToleranceDays() {
        return lateToleranceDays;
    }

    BigDecimal minimumDeliveryRatio() {
        return minimumDeliveryRatio;
    }

    long priorityRiskDays() {
        return priorityRiskDays;
    }

    private static void validateLong(Map<String, Object> config, String field, List<Diagnostic> diagnostics,
            java.util.function.LongPredicate predicate) {
        Object value = config.get(field);
        BigDecimal decimal = value instanceof Number number ? decimalOrNull(number) : null;
        if (decimal == null || !isIntegral(decimal)
                || decimal.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0
                || decimal.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                || !predicate.test(decimal.longValue())) {
            diagnostics.add(error("/config/" + field, "configuration field must be a valid non-negative integer"));
        }
    }

    private static void validateRatio(Map<String, Object> config, List<Diagnostic> diagnostics) {
        Object value = config.get(DELIVERY_RATIO);
        if (!(value instanceof Number number)) {
            diagnostics.add(error("/config/" + DELIVERY_RATIO,
                    "configuration field must be a number between 0 and 1"));
            return;
        }
        BigDecimal ratio = decimalOrNull(number);
        if (ratio == null || ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            diagnostics.add(error("/config/" + DELIVERY_RATIO,
                    "configuration field must be greater than 0 and at most 1"));
        }
    }

    private static boolean isIntegral(BigDecimal decimal) {
        return decimal.stripTrailingZeros().scale() <= 0;
    }

    private static long numberAsLong(Object value) {
        Number number = (Number) value;
        BigDecimal decimal = numberAsDecimal(number);
        if (!isIntegral(decimal) || decimal.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0
                || decimal.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("expected integral threshold");
        }
        return decimal.longValue();
    }

    private static BigDecimal numberAsDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(number.toString());
    }

    private static BigDecimal decimalOrNull(Number number) {
        try {
            return numberAsDecimal(number);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Diagnostic error(String path, String message) {
        return new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR, path, message,
                "provide a valid detector threshold");
    }
}
