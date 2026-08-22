package dev.hogwai.platform.host.helidon;

import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationResult;
import dev.hogwai.platform.spi.host.InvocationSuccess;
import dev.hogwai.platform.spi.host.StructuredPayload;
import io.helidon.webserver.http.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static dev.hogwai.platform.host.helidon.http.HttpHelper.getHttpCode;
import static dev.hogwai.platform.host.helidon.http.HttpHelper.sanitize;
import static dev.hogwai.platform.host.helidon.http.HttpHelper.sendResponse;

/** Serializes generic host invocation results as JSON HTTP responses. */
public final class HelidonResponseWriter {

    /** Creates a response writer. */
    public HelidonResponseWriter() {
        // no state
    }

    /** Writes an invocation result. */
    public void write(ServerResponse response, InvocationResult result) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(result, "result must not be null");
        if (result instanceof InvocationSuccess(StructuredPayload payload)) {
            sendResponse(response, 200, payload.value());
        } else if (result instanceof InvocationFailure failure) {
            writeFailure(response, getHttpCode(failure.code()), failure);
        }
    }

    /** Writes a failure with an already selected HTTP status. */
    public void writeFailure(ServerResponse response, int statusCode, InvocationFailure failure) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        if (statusCode < 400 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be an HTTP error status");
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", failure.code().name());
        error.put("message", sanitize(failure.message()));
        sendResponse(response, statusCode, Map.of("error", error));
    }
}
