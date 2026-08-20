package dev.hogwai.platform.spi.data.access;

import java.util.List;

/** Framework-independent contract for executing data queries. */
public interface DataAccess extends AutoCloseable {

    /**
     * Executes a query and maps each returned row.
     *
     * @param request the query request
     * @param context the query context
     * @param <T> the mapped row type
     * @return the mapped rows
     */
    <T> List<T> query(QueryRequest<T> request, QueryContext context);

    /** Closes this data access and releases its owned resources. */
    @Override
    void close();
}
