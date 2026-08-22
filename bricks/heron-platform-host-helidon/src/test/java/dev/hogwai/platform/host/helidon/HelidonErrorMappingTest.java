package dev.hogwai.platform.host.helidon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import dev.hogwai.platform.host.helidon.http.HttpHelper;
import dev.hogwai.platform.spi.host.EntrypointDescriptor;
import dev.hogwai.platform.spi.host.FailureCode;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import dev.hogwai.platform.spi.host.InvocationFailure;
import dev.hogwai.platform.spi.host.InvocationRequest;
import dev.hogwai.platform.spi.host.InvocationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonErrorMappingTest {

    @Test
    void mapsFailuresToStableSanitizedResponses() throws Exception {
        for (FailureCode code : FailureCode.values()) {
            try (HelidonHostAdapter adapter = new HelidonHostAdapter();
                 HttpClient client = HttpClient.newHttpClient()) {
                adapter.start(new FailingApplication(code),
                        new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(1)));
                HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + adapter.port() + "/failure")).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(HttpHelper.getHttpCode(code));
                assertThat(response.body()).contains("\"code\":\"" + code.name() + "\"")
                        .contains(code == FailureCode.INTERNAL
                                ? "internal invocation failure" : "safe failure message")
                        .doesNotContain("\n")
                        .doesNotContain("java.lang")
                        .doesNotContain("stack");
                assertThat(response.headers().firstValue("content-type"))
                        .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
            }
        }
    }

    @Test
    void exposesTheStableStatusTable() {
        assertThat(HttpHelper.getHttpCode(FailureCode.INVALID_REQUEST)).isEqualTo(400);
        assertThat(HttpHelper.getHttpCode(FailureCode.ENTRYPOINT_NOT_FOUND)).isEqualTo(404);
        assertThat(HttpHelper.getHttpCode(FailureCode.CONFIGURATION)).isEqualTo(409);
        assertThat(HttpHelper.getHttpCode(FailureCode.PROVIDER)).isEqualTo(422);
        assertThat(HttpHelper.getHttpCode(FailureCode.DEADLINE_EXCEEDED)).isEqualTo(408);
        assertThat(HttpHelper.getHttpCode(FailureCode.CANCELLATION_REQUESTED)).isEqualTo(499);
        assertThat(HttpHelper.getHttpCode(FailureCode.INTERNAL)).isEqualTo(500);
    }

    private record FailingApplication(FailureCode code) implements HostApplication {

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
                // No-op
            }
        }
}
