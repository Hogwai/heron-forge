package dev.hogwai.platform.data.jdbi;

import dev.hogwai.platform.spi.data.access.DataRow;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Result-set-backed implementation of the framework-independent row contract. */
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

    /** {@inheritDoc} */
    @Override
    public String string(String column) {
        Object value = value(column);
        if (value instanceof String string) {
            return string;
        }
        throw incompatible(column, String.class);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public Instant instant(String column) {
        validateColumn(column);
        try {
            OffsetDateTime offsetDateTime = resultSet.getObject(column, OffsetDateTime.class);
            if (offsetDateTime == null) {
                throw new IllegalArgumentException("Column '" + column + "' is SQL NULL");
            }
            return offsetDateTime.toInstant();
        } catch (SQLException failure) {
            throw new IllegalArgumentException("Column '" + column + "' is absent or incompatible with Instant");
        }
    }

    private Object value(String column) {
        validateColumn(column);
        try {
            Object value = resultSet.getObject(column);
            if (value == null) {
                throw new IllegalArgumentException("Column '" + column + "' is SQL NULL");
            }
            return value;
        } catch (SQLException failure) {
            throw new IllegalArgumentException("Column '" + column + "' is absent or inaccessible");
        }
    }

    private static void validateColumn(String column) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Column name must not be blank");
        }
    }

    private static IllegalArgumentException incompatible(String column, Class<?> expectedType) {
        return new IllegalArgumentException("Column '" + column + "' is incompatible with "
                + expectedType.getSimpleName());
    }
}
