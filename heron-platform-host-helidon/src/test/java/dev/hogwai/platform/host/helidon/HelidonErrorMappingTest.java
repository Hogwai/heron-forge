package dev.hogwai.platform.host.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.host.api.EntrypointDescriptor;
import dev.hogwai.platform.host.api.FailureCode;
import dev.hogwai.platform.host.api.HostApplication;
import dev.hogwai.platform.host.api.HostConfiguration;
import dev.hogwai.platform.host.api.InvocationFailure;
import dev.hogwai.platform.host.api.InvocationRequest;
import dev.hogwai.platform.host.api.InvocationResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class HelidonErrorMappingTest {

    @Test
    void mapsFailuresToStableSanitizedResponses() throws Exception {
        for (FailureCode code : FailureCode.values()) {
            HelidonHostAdapter adapter = new HelidonHostAdapter();
            try (HttpClient client = HttpClient.newHttpClient()) {
                adapter.start(new FailingApplication(code),
                        new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(1)));
                HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + adapter.port() + "/failure")).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(HelidonResponseWriter.statusFor(code));
                assertThat(response.body()).contains("\"code\":\"" + code.name() + "\"")
                        .contains(code == FailureCode.INTERNAL
                                ? "internal invocation failure" : "safe failure message")
                        .doesNotContain("\n")
                        .doesNotContain("java.lang")
                        .doesNotContain("stack");
                assertThat(response.headers().firstValue("content-type"))
                        .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
            } finally {
                adapter.close();
            }
        }
    }

    @Test
    void exposesTheStableStatusTable() {
        assertThat(HelidonResponseWriter.statusFor(FailureCode.INVALID_REQUEST)).isEqualTo(400);
        assertThat(HelidonResponseWriter.statusFor(FailureCode.ENTRYPOINT_NOT_FOUND)).isEqualTo(404);
        assertThat(HelidonResponseWriter.statusFor(FailureCode.CONFIGURATION)).isEqualTo(409);
        assertThat(HelidonResponseWriter.statusFor(FailureCode.PROVIDER)).isEqualTo(422);
        assertThat(HelidonResponseWriter.statusFor(FailureCode.DEADLINE_EXCEEDED)).isEqualTo(408);
        assertThat(HelidonResponseWriter.statusFor(FailureCode.CANCELLATION_REQUESTED)).isEqualTo(499);
        assertThat(HelidonResponseWriter.statusFor(FailureCode.INTERNAL)).isEqualTo(500);
    }

    private static final class FailingApplication implements HostApplication {
        private final FailureCode code;

        private FailingApplication(FailureCode code) {
            this.code = code;
        }

        @Override
        public List<EntrypointDescriptor> entrypoints() {
            return List.of(new EntrypointDescriptor("failure", "/failure"));
        }

        @Override
        public InvocationResult invoke(InvocationRequest request) {
            if (code == FailureCode.INTERNAL) {
                throw new IllegalStateException("sensitive provider detail");
            }
            return new InvocationFailure(code, "safe\nfailure message");
        }

        @Override
        public void close() {
        }
    }
}
