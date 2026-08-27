package dev.hogwai.platform.spi.data.access;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.SchemaRecord;
import dev.hogwai.platform.spi.data.StreamingDataSet;

import java.util.List;
import java.util.Map;

/**
 * Contract for executing data queries.
 *
 * <p>The low-level {@link #query} primitive maps rows through a caller-supplied
 * {@link RowMapper}. The higher-level {@link #queryToDataSet} methods execute a
 * query and map each row into a {@link SchemaRecord}
 * conforming to the supplied {@link Schema}, returning a
 * {@link MaterializedDataSet}. Implementations must implement every declared
 * method; an implementation that does not support an operation throws
 * {@link UnsupportedOperationException}.
 */
public interface DataAccess extends AutoCloseable {

    /**
     * Executes a query and maps each returned row.
     *
     * @param request the query request
     * @param context the query context
     * @param <T>     the mapped row type
     * @return the mapped rows
     */
    <T> List<T> query(QueryRequest<T> request, QueryContext context);

    /**
     * Executes a query and returns a materialized data set conforming to the
     * supplied schema.
     *
     * @param context       the query context
     * @param operation     the logical operation name
     * @param sql           the SQL statement
     * @param schema        the schema the returned records conform to
     * @param columnByField the mapping from schema field name to source column
     * @return the materialized data set
     */
    MaterializedDataSet queryToDataSet(QueryContext context,
                                       String operation,
                                       String sql,
                                       Schema schema,
                                       Map<String, String> columnByField);

    /**
     * Executes a query and returns a materialized data set conforming to the
     * supplied schema, bounded by the supplied limits.
     *
     * @param context       the query context
     * @param operation     the logical operation name
     * @param sql           the SQL statement
     * @param schema        the schema the returned records conform to
     * @param columnByField the mapping from schema field name to source column
     * @param limits        the dataset limits
     * @return the materialized data set
     */
    MaterializedDataSet queryToDataSet(QueryContext context,
                                       String operation,
                                       String sql,
                                       Schema schema,
                                       Map<String, String> columnByField,
                                       DataSetLimits limits);

    /**
     * Executes a parameterized query and returns a materialized data set
     * conforming to the supplied schema.
     *
     * @param context       the query context
     * @param operation     the logical operation name
     * @param sql           the SQL statement
     * @param parameters    the named parameters bound to the SQL
     * @param schema        the schema the returned records conform to
     * @param columnByField the mapping from schema field name to source column
     * @return the materialized data set
     */
    MaterializedDataSet queryToDataSet(QueryContext context,
                                       String operation,
                                       String sql,
                                       Map<String, ?> parameters,
                                       Schema schema,
                                       Map<String, String> columnByField);

    /**
     * Executes a parameterized query and returns a materialized data set
     * conforming to the supplied schema, bounded by the supplied limits.
     *
     * @param context       the query context
     * @param operation     the logical operation name
     * @param sql           the SQL statement
     * @param parameters    the named parameters bound to the SQL
     * @param schema        the schema the returned records conform to
     * @param columnByField the mapping from schema field name to source column
     * @param limits        the dataset limits
     * @return the materialized data set
     */
    MaterializedDataSet queryToDataSet(QueryContext context,
                                       String operation,
                                       String sql,
                                       Map<String, ?> parameters,
                                       Schema schema,
                                       Map<String, String> columnByField,
                                       DataSetLimits limits);

    /**
     * Executes a query and returns a lazy, bounded streaming data set conforming
     * to the supplied schema.
     *
     * <p>Records are pulled in batches; the returned dataset owns the underlying
     * cursor, so closing it releases the database resources. Registering the
     * dataset with a resource tracker covers its whole streaming lifetime.
     *
     * @param context       the query context
     * @param operation     the logical operation name
     * @param sql           the SQL statement
     * @param schema        the schema the streamed records conform to
     * @param columnByField the mapping from schema field name to source column
     * @param limits        the cumulative dataset limits enforced while streaming
     * @param batchSize     the maximum number of records per pulled batch
     * @return the streaming data set
     */
    StreamingDataSet streamQuery(QueryContext context,
                                 String operation,
                                 String sql,
                                 Schema schema,
                                 Map<String, String> columnByField,
                                 DataSetLimits limits,
                                 int batchSize);

    /**
     * Executes a write statement and returns the number of affected rows.
     *
     * @param context    the query context
     * @param operation  the logical operation name
     * @param sql        the SQL statement
     * @param parameters the named parameters bound to the SQL
     * @return the number of affected rows
     * @throws UnsupportedOperationException if this implementation does not support writes
     */
    int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters);

    /**
     * Closes this data access and releases its owned resources.
     */
    @Override
    void close();
}