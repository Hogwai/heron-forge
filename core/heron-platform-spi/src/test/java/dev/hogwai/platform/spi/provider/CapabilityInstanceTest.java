package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.DataSetMetadata;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CapabilityInstanceTest {

    private static final CapabilityInstance INSTANCE = (_, _) ->
            new MaterializedDataSet(ProviderTestSupport.schema("s"),
                    List.of(),
                    new DataSetMetadata("ds",new DataSetLimits(10, 1000)),
                    0);

    @Test
    void defaultCloseIsNoOp() {
        assertThatCode(INSTANCE::close).doesNotThrowAnyException();
    }

    @Test
    void executeReturnsDataSet() {
        MaterializedDataSet result = INSTANCE.execute(CapabilityInputs.of(List.of()),
                new ExecutionContext("req", "snap", java.time.Instant.now(),
                        () -> false, "corr"));
        assertThat(result.schema().identifier()).isEqualTo("s");
    }
}
