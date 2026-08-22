package dev.hogwai.platform.spi.data.access;

import java.time.Instant;

/**
 * A typed view of one returned data row.
 *
 * <p>A row is valid only during the call to the {@link RowMapper} that receives
 * it. Implementations may be backed by a live database cursor, so callers must
 * not retain a row reference after that mapper call returns.
 *
 * <p>Each accessor throws {@link IllegalArgumentException} when the named
 * column is absent, contains SQL NULL, or has a value incompatible with the
 * requested type.
 */
public interface DataRow {

    /**
     * Returns the string value of the named column in this row.
     *
     * @param column the column name
     * @return the column value
     * @throws IllegalArgumentException if the column is absent, SQL NULL, or
     *                                  incompatible with {@code String}
     */
    String string(String column);

    /**
     * Returns the long value of the named column in this row.
     *
     * @param column the column name
     * @return the column value
     * @throws IllegalArgumentException if the column is absent, SQL NULL, or
     *                                  incompatible with {@code long}
     */
    long longValue(String column);

    /**
     * Returns the instant value of the named column in this row.
     *
     * @param column the column name
     * @return the column value
     * @throws IllegalArgumentException if the column is absent, SQL NULL, or
     *                                  incompatible with {@code Instant}
     */
    Instant instant(String column);
}
