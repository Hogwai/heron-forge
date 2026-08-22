package dev.hogwai.platform.spi.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable description of the type of a {@link Field}.
 *
 * <p>The set of supported types is intentionally closed (v1) and does not
 * perform any implicit numeric widening. Framework-independent and immutable.
 */
public sealed interface FieldType
        permits FieldType.StringType, FieldType.BooleanType, FieldType.Int64Type,
                FieldType.DecimalType, FieldType.InstantType, FieldType.EnumType {

    /**
     * Returns whether the given value is assignable to this type.
     *
     * @param value the value to test, or {@code null}
     * @return {@code true} if the value is assignable to this type
     */
    boolean accepts(Object value);

    /** A UTF-8 string value. */
    record StringType() implements FieldType {
        @Override
        public boolean accepts(Object value) {
            return value instanceof String;
        }
    }

    /** A boolean value. */
    record BooleanType() implements FieldType {
        @Override
        public boolean accepts(Object value) {
            return value instanceof Boolean;
        }
    }

    /** A 64-bit signed integer value. */
    record Int64Type() implements FieldType {
        @Override
        public boolean accepts(Object value) {
            return value instanceof Long;
        }
    }

    /** An arbitrary-precision decimal value. */
    record DecimalType() implements FieldType {
        @Override
        public boolean accepts(Object value) {
            return value instanceof BigDecimal;
        }
    }

    /** An instant in time. */
    record InstantType() implements FieldType {
        @Override
        public boolean accepts(Object value) {
            return value instanceof Instant;
        }
    }

    /**
     * An enumerated string value drawn from a fixed symbol set.
     *
     * @param definition the enum definition
     */
    record EnumType(EnumDefinition definition) implements FieldType {
        /**
         * Creates an enum type.
         *
         * @param definition the enum definition
         * @throws NullPointerException if {@code definition} is {@code null}
         */
        public EnumType {
            Objects.requireNonNull(definition, "definition must not be null");
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof String symbol && definition.symbols().contains(symbol);
        }
    }
}
