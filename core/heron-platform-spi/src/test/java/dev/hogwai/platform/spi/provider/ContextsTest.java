package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextsTest {

    private static final DataAccessFactory DATA_ACCESS_FACTORY = _ -> new DataAccess() {
        @Override
        public <T> List<T> query(QueryRequest<T> request, QueryContext context) {
            return List.of();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Schema schema,
                                                  Map<String, String> columnByField) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Map<String, ?> parameters,
                                                  Schema schema,
                                                  Map<String, String> columnByField) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Schema schema,
                                                  Map<String, String> columnByField,
                                                  DataSetLimits limits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterializedDataSet queryToDataSet(QueryContext context,
                                                  String operation,
                                                  String sql,
                                                  Map<String, ?> parameters,
                                                  Schema schema,
                                                  Map<String, String> columnByField,
                                                  DataSetLimits limits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingDataSet streamQuery(QueryContext context,
                                            String operation,
                                            String sql,
                                            Schema schema,
                                            Map<String, String> columnByField,
                                            DataSetLimits limits,
                                            int batchSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(QueryContext context,
                           String operation,
                           String sql,
                           Map<String, ?> parameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no resources
        }
    };

    @Test
    void buildContextExposesIntendedData() {
        Clock clock = Clock.systemUTC();
        ResourceTracker tracker = _ -> {};
        BuildContext context = new BuildContext(clock, tracker, DATA_ACCESS_FACTORY);
        assertThat(context.clock()).isSameAs(clock);
        assertThat(context.resourceTracker()).isSameAs(tracker);
        assertThat(context.dataAccessFactory()).isSameAs(DATA_ACCESS_FACTORY);
    }

    @Test
    void buildContextRejectsNullArguments() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new BuildContext(null, _ -> {
        }, DATA_ACCESS_FACTORY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuildContext(clock, _ -> {
        }, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildContextExposesDocumentedApi() throws NoSuchMethodException {
        assertThat(BuildContext.class.getConstructor(Clock.class, ResourceTracker.class, DataAccessFactory.class))
                .isNotNull();
        assertThat(BuildContext.class.getMethod("clock")).isNotNull();
        assertThat(BuildContext.class.getMethod("resourceTracker")).isNotNull();
        assertThat(BuildContext.class.getMethod("dataAccessFactory")).isNotNull();
    }

}
