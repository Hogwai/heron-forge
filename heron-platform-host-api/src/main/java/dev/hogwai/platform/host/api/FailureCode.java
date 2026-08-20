package dev.hogwai.platform.host.api;

/** Stable failure categories exposed by the host boundary. */
public enum FailureCode {
    /** Request was invalid. */
    INVALID_REQUEST,
    /** Entrypoint was not configured. */
    ENTRYPOINT_NOT_FOUND,
    /** Host or application configuration failed. */
    CONFIGURATION,
    /** Provider execution failed. */
    PROVIDER,
    /** Request deadline was exceeded. */
    DEADLINE_EXCEEDED,
    /** Cancellation was requested. */
    CANCELLATION_REQUESTED,
    /** An unexpected internal failure occurred. */
    INTERNAL
}
