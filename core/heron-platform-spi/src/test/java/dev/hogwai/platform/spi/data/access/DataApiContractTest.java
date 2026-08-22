package dev.hogwai.platform.spi.data.access;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataApiContractTest {

    private static final Instant DEADLINE = Instant.parse("2030-01-01T00:00:00Z");
    private static final RowMapper<String> MAPPER = row -> row.string("name");
    public static final String SELECT_1 = "select 1";
    public static final String SECRET = "secret";
    public static final String OPTIONAL = "optional";
    public static final String JDBC_TEST = "jdbc:test";

    @Test
    void validatesDataAccessConfiguration() {
        assertThatThrownBy(() -> new DataAccessConfiguration(null, "user", SECRET))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("url must not be null");
        assertThatThrownBy(() -> new DataAccessConfiguration(" ", "user", SECRET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url must not be blank");
        assertThatThrownBy(() -> new DataAccessConfiguration(JDBC_TEST, null, SECRET))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("username must not be null");
        assertThatThrownBy(() -> new DataAccessConfiguration(JDBC_TEST, "\t", SECRET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> new DataAccessConfiguration(JDBC_TEST, "user", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("password must not be null");
        assertThat(new DataAccessConfiguration("url", "user", "")).isNotNull();

        String secret = "super-secret";
        String description = new DataAccessConfiguration("url", "user", secret).toString();
        assertThat(description).doesNotContain(secret);
    }

    @Test
    void validatesQueryContext() {
        assertThatThrownBy(() -> new QueryContext(null, () -> false))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("deadline must not be null");
        assertThatThrownBy(() -> new QueryContext(DEADLINE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cancellationSignal must not be null");

        AtomicBoolean cancelled = new AtomicBoolean();
        BooleanSupplier cancellationSignal = cancelled::get;
        QueryContext context = new QueryContext(DEADLINE, cancellationSignal);
        assertThat(context.deadline()).isEqualTo(DEADLINE);
        assertThat(context.cancellationSignal()).isSameAs(cancellationSignal);
        assertThat(context.isCancellationRequested()).isFalse();
        cancelled.set(true);
        assertThat(context.isCancellationRequested()).isTrue();
    }

    @Test
    void validatesQueryRequest() {
        Map<String, Object> parameters = Map.of("limit", 10);
        assertThatThrownBy(() -> new QueryRequest<>(null, SELECT_1, parameters, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operation must not be null");
        assertThatThrownBy(() -> new QueryRequest<>(" ", SELECT_1, parameters, MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation must not be blank");
        assertThatThrownBy(() -> new QueryRequest<>("read", null, parameters, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sql must not be null");
        assertThatThrownBy(() -> new QueryRequest<>("read", "\n", parameters, MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sql must not be blank");
        assertThatThrownBy(() -> new QueryRequest<>("read", SELECT_1, null, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parameters must not be null");
        var map = Map.of(" ", 10);
        assertThatThrownBy(() -> new QueryRequest<>("read", SELECT_1, map, MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parameter key must not be blank");
        Map<String, Object> nullKey = new LinkedHashMap<>();
        nullKey.put(null, 10);
        assertThatThrownBy(() -> new QueryRequest<>("read", SELECT_1, nullKey, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parameter key must not be null");
        assertThatThrownBy(() -> new QueryRequest<String>("read", SELECT_1, parameters, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void copiesAndFreezesQueryParameters() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        QueryRequest<String> request = new QueryRequest<>("find", "select", source, MAPPER);

        source.put("changed", true);
        Map<String, Object> parameters = (Map<String, Object>) request.parameters();
        assertThat(parameters).hasSize(1)
                .containsEntry("name", "Ada")
                .isNotSameAs(source);
        assertThatThrownBy(parameters::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        source.put(OPTIONAL, null);
        QueryRequest<String> requestWithNull = new QueryRequest<>("find", "select", source, MAPPER);
        assertThat(requestWithNull.parameters()).containsKey(OPTIONAL);
        assertThat(requestWithNull.parameters().get(OPTIONAL)).isNull();
    }

    @Test
    void dataAccessImplementationsMustCloseExplicitly() {
        AtomicBoolean closed = new AtomicBoolean();
        DataAccess access = new DataAccess() {
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
                    Map<String, ?> parameters, Schema schema, Map<String, String> columnByField,
                    DataSetLimits limits) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int execute(QueryContext context, String operation, String sql, Map<String, ?> parameters) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        access.close();
        assertThat(closed).isTrue();
    }

    @Test
    void dataAccessCloseMethodIsAbstract() throws NoSuchMethodException {
        int modifiers = DataAccess.class.getDeclaredMethod("close").getModifiers();

        assertThat(Modifier.isAbstract(modifiers)).isTrue();
    }
}
