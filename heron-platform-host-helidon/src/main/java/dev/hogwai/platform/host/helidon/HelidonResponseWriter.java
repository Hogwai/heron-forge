package dev.hogwai.platform.host.helidon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hogwai.platform.host.api.FailureCode;
import dev.hogwai.platform.host.api.InvocationFailure;
import dev.hogwai.platform.host.api.InvocationResult;
import dev.hogwai.platform.host.api.InvocationSuccess;
import io.helidon.webserver.http.ServerResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Serializes generic host invocation results as JSON HTTP responses. */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class HelidonResponseWriter {

    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private final ObjectMapper objectMapper;

    /** Creates a response writer with the default Jackson mapper. */
    public HelidonResponseWriter() {
        objectMapper = new ObjectMapper();
    }

    /** Writes an invocation result. */
    public void write(ServerResponse response, InvocationResult result) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(result, "result must not be null");
        if (result instanceof InvocationSuccess success) {
            byte[] bytes;
            try {
                bytes = objectMapper.writeValueAsBytes(success.payload().value());
            } catch (JsonProcessingException | RuntimeException exception) {
                writeFailure(response, 500,
                        new InvocationFailure(FailureCode.INTERNAL, "internal response serialization failure"));
                return;
            }
            sendJson(response, 200, bytes);
        } else if (result instanceof InvocationFailure failure) {
            writeFailure(response, statusFor(failure.code()), failure);
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
        try {
            sendJson(response, statusCode, objectMapper.writeValueAsBytes(Map.of("error", error)));
        } catch (JsonProcessingException exception) {
            sendJson(response, 500, ("{\"error\":{\"code\":\"INTERNAL\","
                    + "\"message\":\"internal response serialization failure\"}}")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Returns the stable HTTP status associated with a host failure. */
    public static int statusFor(FailureCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return switch (code) {
            case INVALID_REQUEST -> 400;
            case ENTRYPOINT_NOT_FOUND -> 404;
            case CONFIGURATION -> 409;
            case PROVIDER -> 422;
            case DEADLINE_EXCEEDED -> 408;
            case CANCELLATION_REQUESTED -> 499;
            case INTERNAL -> 500;
        };
    }

    private static void sendJson(ServerResponse response, int statusCode, byte[] bytes) {
        response.status(statusCode).header("content-type", JSON_CONTENT_TYPE).send(bytes);
    }

    private static String sanitize(String message) {
        StringBuilder safe = new StringBuilder(Math.min(message.length(), MAX_MESSAGE_LENGTH));
        message.codePoints().limit(MAX_MESSAGE_LENGTH).forEach(codePoint -> {
            if (codePoint >= 0x20 && codePoint != 0x7f) {
                safe.appendCodePoint(codePoint);
            } else {
                safe.append(' ');
            }
        });
        return safe.isEmpty() ? "request failed" : safe.toString();
    }
}
