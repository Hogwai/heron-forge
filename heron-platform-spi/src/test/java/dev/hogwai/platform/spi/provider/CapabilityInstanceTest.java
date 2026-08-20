package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.execution.ExecutionContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityInstanceTest {

    private static final CapabilityInstance INSTANCE = new CapabilityInstance() {
        @Override
        public MaterializedDataSet execute(CapabilityInputs inputs, ExecutionContext context) {
            return new MaterializedDataSet(ProviderTestSupport.schema("s"), List.of(),
                    new dev.hogwai.platform.spi.data.DataSetMetadata("ds",
                            new dev.hogwai.platform.spi.data.DataSetLimits(10, 1000)), 0);
        }
    };

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
