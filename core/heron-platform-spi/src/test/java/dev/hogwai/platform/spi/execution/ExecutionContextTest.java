package dev.hogwai.platform.spi.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    private static final Instant DEADLINE = Instant.parse("2030-01-01T00:00:00Z");

    private static ExecutionContext context() {
        return new ExecutionContext("req-1", "snap-1", DEADLINE, () -> false, "corr-1");
    }

    @Test
    void exposesAllValues() {
        ExecutionContext context = context();
        assertThat(context.requestId()).isEqualTo("req-1");
        assertThat(context.snapshotId()).isEqualTo("snap-1");
        assertThat(context.deadline()).isEqualTo(DEADLINE);
        assertThat(context.cancellationToken().isCancellationRequested()).isFalse();
        assertThat(context.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThatThrownBy(() -> new ExecutionContext("", "snap", DEADLINE, () -> false, "corr"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionContext("req", " ", DEADLINE, () -> false, "corr"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionContext("req", "snap", DEADLINE, () -> false, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new ExecutionContext(null, "snap", DEADLINE, () -> false, "corr"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExecutionContext("req", null, DEADLINE, () -> false, "corr"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExecutionContext("req", "snap", null, () -> false, "corr"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExecutionContext("req", "snap", DEADLINE, null, "corr"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExecutionContext("req", "snap", DEADLINE, () -> false, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposesOnlyDocumentedApi() {
        // Structural guard: exactly one public constructor, no public fields and
        // exactly the documented zero-arg accessors. Any added logger, metrics,
        // event sink, secret String or raw payload member fails this assertion.
        ApiAssert.assertPublicApi(ExecutionContext.class,
                new Class<?>[]{String.class, String.class, Instant.class, CancellationToken.class, String.class},
                Set.of(
                        ApiAssert.MethodSpec.of("requestId", String.class),
                        ApiAssert.MethodSpec.of("snapshotId", String.class),
                        ApiAssert.MethodSpec.of("deadline", Instant.class),
                        ApiAssert.MethodSpec.of("cancellationToken", CancellationToken.class),
                        ApiAssert.MethodSpec.of("correlationId", String.class),
                        // Record API: value equality and rendering are part of
                        // the declared surface since the record conversion.
                        ApiAssert.MethodSpec.of("equals", boolean.class, Object.class),
                        ApiAssert.MethodSpec.of("hashCode", int.class),
                        ApiAssert.MethodSpec.of("toString", String.class)));
    }
}
