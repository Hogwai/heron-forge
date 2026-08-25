package dev.hogwai.platform.host.helidon.http;

import dev.hogwai.platform.spi.host.FailureCode;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("PMD.CyclomaticComplexity")
public final class HttpHelper {

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    public static final int MAX_MESSAGE_LENGTH = 256;


    private HttpHelper() {
    }

    /**
     * Write health response
     *
     * @param response server response
     * @param live liveness
     * @param ready readiness
     */
    public static void writeHealth(ServerResponse response, boolean live, boolean ready) {
        boolean healthy = live || ready;
        int status = healthy ? 200 : 500;
        String readiness = healthy ? "ready" : "not-ready";
        String health = live ? "live" : readiness;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", health);
        response.status(status)
                .header("content-type", JSON_CONTENT_TYPE)
                .send(body);
    }

    /**
     * Extract id from a request
     *
     * @param request request
     * @param headerName header to extract
     * @param fallbackHeader fallback header
     * @return extracted id
     */
    public static String getIdFromHeader(ServerRequest request, String headerName, String fallbackHeader) {
        return request.headers()
                .first(HeaderNames.create(headerName))
                .orElse(fallbackHeader);
    }

    /**
     * Returns the stable HTTP code associated with a host failure.
     *
     * @param code failure code
     * @return http code
     */
    public static int getHttpCode(FailureCode code) {
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

    public static void sendResponse(ServerResponse response, int statusCode, Object body) {
        response.status(statusCode).header("content-type", JSON_CONTENT_TYPE).send(body);
    }

    public static String sanitize(String message) {
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
