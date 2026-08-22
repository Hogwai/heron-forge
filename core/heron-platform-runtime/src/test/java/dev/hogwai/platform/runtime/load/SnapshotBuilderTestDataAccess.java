package dev.hogwai.platform.runtime.load;

import java.util.List;
import java.util.Map;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;

/** In-memory data access fixtures for snapshot ownership tests. */
final class SnapshotBuilderTestDataAccessFactory implements DataAccessFactory {

    private SnapshotBuilderTestDataAccess lastOpened;

    @Override
    public DataAccess open(DataAccessConfiguration configuration) {
        lastOpened = new SnapshotBuilderTestDataAccess();
        return lastOpened;
    }

    SnapshotBuilderTestDataAccess lastOpened() {
        return lastOpened;
    }
}

final class SnapshotBuilderTestDataAccess implements DataAccess {

    private boolean closed;

    @Override
    public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
        return List.of();
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Schema schema, Map<String, String> columnByField) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Map<String, ?> parameters, Schema schema, Map<String, String> columnByField) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Schema schema, Map<String, String> columnByField, DataSetLimits limits) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MaterializedDataSet queryToDataSet(QueryContext context, String operation, String sql,
            Map<String, ?> parameters, Schema schema, Map<String, String> columnByField, DataSetLimits limits) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }
}
