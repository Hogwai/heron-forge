package dev.hogwai.platform.data.jdbi;

import dev.hogwai.platform.spi.data.access.DataRow;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Result-set-backed implementation of the row contract.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class JdbiDataRow implements DataRow {

    private final ResultSet resultSet;

    /**
     * Creates a row view over the current result-set row.
     *
     * @param resultSet the current result-set row
     */
    JdbiDataRow(ResultSet resultSet) {
        this.resultSet = Objects.requireNonNull(resultSet, "resultSet must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String string(String column) {
        Object value = value(column);
        if (value instanceof String string) {
            return string;
        }
        throw incompatible(column, String.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long longValue(String column) {
        Object value = value(column);
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException _) {
                throw incompatible(column, Long.class);
            }
        }
        throw incompatible(column, Long.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant instant(String column) {
        validateColumn(column);
        try {
            OffsetDateTime offsetDateTime = resultSet.getObject(column, OffsetDateTime.class);
            if (offsetDateTime == null) {
                throw new IllegalArgumentException("Column '%s' is SQL NULL".formatted(column));
            }
            return offsetDateTime.toInstant();
        } catch (SQLException _) {
            throw new IllegalArgumentException("Column '%s' is absent or incompatible with Instant".formatted(column));
        }
    }

    private Object value(String column) {
        validateColumn(column);
        try {
            Object value = resultSet.getObject(column);
            if (value == null) {
                throw new IllegalArgumentException("Column '%s' is SQL NULL".formatted(column));
            }
            return value;
        } catch (SQLException _) {
            throw new IllegalArgumentException("Column '%s' is absent or inaccessible".formatted(column));
        }
    }

    private static void validateColumn(String column) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Column name must not be blank");
        }
    }

    private static IllegalArgumentException incompatible(String column, Class<?> expectedType) {
        return new IllegalArgumentException("Column '%s' is incompatible with %s"
                .formatted(column, expectedType.getSimpleName()));
    }
}
