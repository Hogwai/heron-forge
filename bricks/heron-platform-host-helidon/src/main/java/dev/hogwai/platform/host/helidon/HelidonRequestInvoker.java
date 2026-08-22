package dev.hogwai.platform.host.helidon;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.hogwai.platform.spi.host.CancellationSignal;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.FailureCode;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.host.InvocationResult;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static dev.hogwai.platform.host.helidon.http.HttpHelper.getIdFromHeader;

/** Handles one HTTP request for a host entrypoint. */
final class HelidonRequestInvoker {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final HostApplication application;
    private final HostConfiguration configuration;
    private final HelidonResponseWriter responseWriter;

    HelidonRequestInvoker(HostApplication application, HostConfiguration configuration,
            HelidonResponseWriter responseWriter) {
        this.application = Objects.requireNonNull(application, "application must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    void invoke(EntrypointDescriptor descriptor, ServerRequest request, ServerResponse response) {
        String requestId = getIdFromHeader(request, REQUEST_ID_HEADER, newId());
        String correlationId = getIdFromHeader(request, CORRELATION_ID_HEADER, requestId);
        if (isMetadataInvalid(requestId) || isMetadataInvalid(correlationId)) {
            responseWriter.writeFailure(response, 400,
                    new InvocationFailure(FailureCode.INVALID_REQUEST, "invalid request metadata"));
            return;
        }
        response.header(REQUEST_ID_HEADER, requestId).header(CORRELATION_ID_HEADER, correlationId);

        Instant deadline = deadline(configuration.requestTimeout());
        CancellationSignal cancellation = () -> !Instant.now().isBefore(deadline)
                || Thread.currentThread().isInterrupted();
        InvocationResult result;
        try {
            result = application.invoke(
                    new InvocationRequest(descriptor.id(), requestId, correlationId, deadline, cancellation));
        } catch (RuntimeException _) {
            result = new InvocationFailure(FailureCode.INTERNAL, "internal invocation failure");
        }
        if (result == null) {
            result = new InvocationFailure(FailureCode.INTERNAL, "internal invocation failure");
        }
        if (!Instant.now().isBefore(deadline)) {
            result = new InvocationFailure(FailureCode.DEADLINE_EXCEEDED, "deadline exceeded");
        }
        responseWriter.write(response, result);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static Instant deadline(Duration timeout) {
        try {
            return Instant.now().plus(timeout);
        } catch (ArithmeticException _) {
            return Instant.MAX;
        }
    }

    private static boolean isMetadataInvalid(String value) {
        return value.isBlank() || value.length() > 256 || value.codePoints().anyMatch(Character::isISOControl);
    }
}