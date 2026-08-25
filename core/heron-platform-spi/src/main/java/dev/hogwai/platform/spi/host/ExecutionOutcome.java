package dev.hogwai.platform.spi.host;

import java.util.Objects;
import java.util.Optional;

/**
 * Shape-adaptive outcome of a single host application execution: either
 * materialized rows, a generic streaming payload, or a failure.
 *
 * <p>Hosts branch once on the outcome instead of probing separate invocation
 * methods, so the underlying graph runs exactly once per request.
 */
public final class ExecutionOutcome {

    private final StructuredPayload materialized;
    private final StreamingPayload streaming;
    private final InvocationFailure failure;

    private ExecutionOutcome(StructuredPayload materialized, StreamingPayload streaming, InvocationFailure failure) {
        this.materialized = materialized;
        this.streaming = streaming;
        this.failure = failure;
    }

    /**
     * Creates an outcome carrying materialized rows.
     *
     * @param payload the structured payload
     * @return the materialized outcome
     */
    public static ExecutionOutcome materialized(StructuredPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return new ExecutionOutcome(payload, null, null);
    }

    /**
     * Creates an outcome carrying a streaming payload. The receiver owns the
     * payload and must close it.
     *
     * @param payload the streaming payload
     * @return the streaming outcome
     */
    public static ExecutionOutcome streaming(StreamingPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return new ExecutionOutcome(null, payload, null);
    }

    /**
     * Creates an outcome describing a failure.
     *
     * @param failure the failure result
     * @return the failure outcome
     */
    public static ExecutionOutcome failure(InvocationFailure failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        return new ExecutionOutcome(null, null, failure);
    }

    /**
     * Returns whether this outcome carries a streaming payload.
     *
     * @return {@code true} when streaming
     */
    public boolean isStreaming() {
        return streaming != null;
    }

    /**
     * Returns the materialized payload when present.
     *
     * @return the structured payload, or empty
     */
    public Optional<StructuredPayload> materialized() {
        return Optional.ofNullable(materialized);
    }

    /**
     * Returns the streaming payload when present.
     *
     * @return the streaming payload, or empty
     */
    public Optional<StreamingPayload> streaming() {
        return Optional.ofNullable(streaming);
    }

    /**
     * Returns the failure when present.
     *
     * @return the failure, or empty
     */
    public Optional<InvocationFailure> failure() {
        return Optional.ofNullable(failure);
    }
}
