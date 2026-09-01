package dev.hogwai.platform.host.helidon;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.hogwai.platform.spi.host.FailureCode;
import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationResult;
import dev.hogwai.platform.spi.host.InvocationSuccess;
import dev.hogwai.platform.spi.host.StreamingPayload;
import dev.hogwai.platform.spi.host.StructuredPayload;
import io.helidon.webserver.http.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.hogwai.platform.host.helidon.http.HttpHelper.getHttpCode;
import static dev.hogwai.platform.host.helidon.http.HttpHelper.sanitize;
import static dev.hogwai.platform.host.helidon.http.HttpHelper.sendResponse;

/**
 * Serializes generic host invocation results as JSON HTTP responses.
 */
public final class HelidonResponseWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelidonResponseWriter.class);
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    public static final String RESPONSE_MUST_NOT_BE_NULL = "response must not be null";
    public static final String RESULT_MUST_NOT_BE_NULL = "result must not be null";
    public static final String FAILURE_MUST_NOT_BE_NULL = "failure must not be null";
    public static final String PAYLOAD_MUST_NOT_BE_NULL = "payload must not be null";

    /**
     * Creates a response writer.
     */
    public HelidonResponseWriter() {
        // no state
    }

    /**
     * Writes an invocation result.
     */
    public void write(ServerResponse response, InvocationResult result) {
        Objects.requireNonNull(response, RESPONSE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(result, RESULT_MUST_NOT_BE_NULL);
        if (result instanceof InvocationSuccess(StructuredPayload payload)) {
            sendResponse(response, 200, payload.value());
        } else if (result instanceof InvocationFailure failure) {
            writeFailure(response, getHttpCode(failure.code()), failure);
        }
    }

    /**
     * Writes a streaming result: the first batch is pulled before any header is
     * sent so early failures keep regular error responses; afterward the body
     * streams incrementally and a mid-stream failure can only truncate it.
     *
     * @param response the server response
     * @param payload  the streamed payload owned by the caller
     */
    public void writeStreaming(ServerResponse response, StreamingPayload payload) {
        Objects.requireNonNull(response, RESPONSE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(payload, PAYLOAD_MUST_NOT_BE_NULL);
        List<Map<String, Object>> firstBatch;
        try {
            firstBatch = payload.nextBatch().orElse(null);
        } catch (RuntimeException failure) {
            writeFailure(response, 500, new InvocationFailure(FailureCode.INTERNAL,
                    sanitize(failure.getMessage() == null ? "stream interrupted" : failure.getMessage())));
            return;
        }
        if (firstBatch == null) {
            sendResponse(response,
                    200,
                    Map.of("rows", List.of(), "rowCount", 0, "schemaId", payload.schemaId(), "schemaVersion", payload.schemaVersion()));
            payload.close();
            return;
        }
        response.status(200).header("content-type", JSON_CONTENT_TYPE);
        try (payload; OutputStream out = response.outputStream()) {
            StreamingJsonEncoder.encode(payload, firstBatch, out);
        } catch (IOException | RuntimeException failure) {
            // Headers are already sent: the body stays truncated, and we log
            // the sanitized diagnostic instead of exposing raw details.
            LOGGER.error("streamed invocation failed: {}",
                    sanitize(failure.getMessage() == null ? "stream interrupted" : failure.getMessage()));
        }
    }

    /**
     * Writes a failure with an already selected HTTP status.
     */
    public void writeFailure(ServerResponse response, int statusCode, InvocationFailure failure) {
        Objects.requireNonNull(response, RESPONSE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(failure, FAILURE_MUST_NOT_BE_NULL);
        if (statusCode < 400 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be an HTTP error status");
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", failure.code().name());
        error.put("message", sanitize(failure.message()));
        sendResponse(response, statusCode, Map.of("error", error));
    }
}
