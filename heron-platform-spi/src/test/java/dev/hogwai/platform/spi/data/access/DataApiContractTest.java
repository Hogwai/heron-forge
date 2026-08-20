package dev.hogwai.platform.spi.data.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class DataApiContractTest {

    private static final Instant DEADLINE = Instant.parse("2030-01-01T00:00:00Z");
    private static final RowMapper<String> MAPPER = row -> row.string("name");

    @Test
    void validatesDataAccessConfiguration() {
        assertThatThrownBy(() -> new DataAccessConfiguration(null, "user", "secret"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("url must not be null");
        assertThatThrownBy(() -> new DataAccessConfiguration(" ", "user", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url must not be blank");
        assertThatThrownBy(() -> new DataAccessConfiguration("jdbc:test", null, "secret"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("username must not be null");
        assertThatThrownBy(() -> new DataAccessConfiguration("jdbc:test", "\t", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> new DataAccessConfiguration("jdbc:test", "user", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("password must not be null");
        assertThat(new DataAccessConfiguration("url", "user", "")).isNotNull();

        String secret = "super-secret";
        String description = new DataAccessConfiguration("url", "user", secret).toString();
        assertThat(description).contains("password=<redacted>").doesNotContain(secret);
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
        assertThatThrownBy(() -> new QueryRequest<>(null, "select 1", parameters, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operation must not be null");
        assertThatThrownBy(() -> new QueryRequest<>(" ", "select 1", parameters, MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation must not be blank");
        assertThatThrownBy(() -> new QueryRequest<>("read", null, parameters, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sql must not be null");
        assertThatThrownBy(() -> new QueryRequest<>("read", "\n", parameters, MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sql must not be blank");
        assertThatThrownBy(() -> new QueryRequest<>("read", "select 1", null, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parameters must not be null");
        assertThatThrownBy(() -> new QueryRequest<>("read", "select 1", Map.of(" ", 10), MAPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parameter key must not be blank");
        Map<String, Object> nullKey = new LinkedHashMap<>();
        nullKey.put(null, 10);
        assertThatThrownBy(() -> new QueryRequest<>("read", "select 1", nullKey, MAPPER))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parameter key must not be null");
        assertThatThrownBy(() -> new QueryRequest<String>("read", "select 1", parameters, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper must not be null");
    }

    @Test
    void copiesAndFreezesQueryParameters() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        QueryRequest<String> request = new QueryRequest<>("find", "select", source, MAPPER);

        source.put("changed", true);
        assertThat(request.parameters()).hasSize(1);
        assertThat(request.parameters().get("name")).isEqualTo("Ada");
        assertThat(request.parameters()).isNotSameAs(source);
        assertThatThrownBy(() -> request.parameters().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        source.put("optional", null);
        QueryRequest<String> requestWithNull = new QueryRequest<>("find", "select", source, MAPPER);
        assertThat(requestWithNull.parameters()).containsKey("optional");
        assertThat(requestWithNull.parameters().get("optional")).isNull();
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
