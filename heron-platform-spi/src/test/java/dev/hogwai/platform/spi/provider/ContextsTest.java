package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessFactory;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ContextsTest {

    private static final DataAccessFactory DATA_ACCESS_FACTORY = configuration -> new DataAccess() {
        @Override
        public <T> java.util.List<T> query(QueryRequest<T> request, QueryContext context) {
            return java.util.List.of();
        }

        @Override
        public void close() {
            // no resources
        }
    };

    @Test
    void buildContextExposesIntendedData() {
        Clock clock = Clock.systemUTC();
        ResourceTracker tracker = resource -> { };
        BuildContext context = new BuildContext(clock, tracker, DATA_ACCESS_FACTORY);
        assertThat(context.clock()).isSameAs(clock);
        assertThat(context.resourceTracker()).isSameAs(tracker);
        assertThat(context.dataAccessFactory()).isSameAs(DATA_ACCESS_FACTORY);
    }

    @Test
    void buildContextRejectsNullArguments() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new BuildContext(null, r -> { }, DATA_ACCESS_FACTORY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuildContext(clock, r -> { }, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildContextExposesDocumentedApi() throws NoSuchMethodException {
        assertThat(BuildContext.class.getConstructor(Clock.class, ResourceTracker.class,
                DataAccessFactory.class)).isNotNull();
        assertThat(BuildContext.class.getMethod("clock")).isNotNull();
        assertThat(BuildContext.class.getMethod("resourceTracker")).isNotNull();
        assertThat(BuildContext.class.getMethod("dataAccessFactory")).isNotNull();
    }

}
