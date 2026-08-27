package dev.hogwai.platform.spi.error;

import dev.hogwai.platform.spi.Diagnostic;

/**
 * Stable error codes carried by {@link PlatformException}
 * and {@link Diagnostic}.
 *
 * <p>The set of codes is intentionally fixed and stable so that consumers can
 * rely on them across versions.
 */
public enum PlatformErrorCode {

    /**
     * The configuration could not be parsed.
     */
    CONFIG_PARSE_ERROR,
    /**
     * The configuration failed schema validation.
     */
    CONFIG_SCHEMA_ERROR,
    /**
     * A referenced provider was not found.
     */
    PROVIDER_NOT_FOUND,
    /**
     * A provider version does not match the required version.
     */
    PROVIDER_VERSION_MISMATCH,
    /**
     * A provider reported a configuration error.
     */
    PROVIDER_CONFIG_ERROR,
    /**
     * A graph references an unknown node or edge.
     */
    GRAPH_REFERENCE_ERROR,
    /**
     * The graph contains a cycle.
     */
    GRAPH_CYCLE_ERROR,
    /**
     * A schema is incompatible with the expected contract.
     */
    SCHEMA_INCOMPATIBLE,
    /**
     * A dataset limit was exceeded.
     */
    DATASET_LIMIT_EXCEEDED,
    /**
     * A capability failed during execution.
     */
    CAPABILITY_EXECUTION_ERROR,
    /**
     * A deadline was exceeded.
     */
    DEADLINE_EXCEEDED,
    /**
     * The operation was canceled.
     */
    CANCELLATION_REQUESTED,
    /**
     * No data access implementation was found on the classpath.
     */
    DATA_ACCESS_UNAVAILABLE
}
