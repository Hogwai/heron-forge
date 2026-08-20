package dev.hogwai.platform.runtime.load;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import java.util.List;

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
    public void close() {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }
}
