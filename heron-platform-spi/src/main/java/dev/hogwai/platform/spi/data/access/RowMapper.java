package dev.hogwai.platform.spi.data.access;

/** Maps one data row to an application value. */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps a row. The row is valid only for the duration of this call and must
     * not be retained by the mapper or the value it returns.
     *
     * @param row the data row
     * @return the mapped value
     */
    T map(DataRow row);
}
