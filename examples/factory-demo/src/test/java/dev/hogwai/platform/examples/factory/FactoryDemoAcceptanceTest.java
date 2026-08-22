package dev.hogwai.platform.examples.factory;

import dev.hogwai.platform.host.helidon.HelidonHostAdapter;
import dev.hogwai.platform.runtime.load.ApplicationLoader;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.host.HostApplication;
import dev.hogwai.platform.spi.host.HostConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** End-to-end acceptance checks for the real factory-demo shell path. */
@SuppressWarnings("PMD.CyclomaticComplexity")
class FactoryDemoAcceptanceTest {

    private static final String CONFIGURATION_RESOURCE = "/supply-chain.yaml";
    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/heron_demo";
    private static final HostConfiguration HOST_CONFIGURATION =
            new HostConfiguration("127.0.0.1", 0, Duration.ofSeconds(5));

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
    void servesHealthAndExceptionsAndAppliesChangedThreshold() throws Exception {
        String yaml = configuration();
        HostApplication application = ApplicationLoader.load(stream(yaml));
        try (HelidonHostAdapter adapter = new HelidonHostAdapter();
             HttpClient client = HttpClient.newHttpClient()) {
            adapter.start(application, HOST_CONFIGURATION);
            URI baseUri = URI.create("http://127.0.0.1:" + adapter.port());

            assertHealth(client, baseUri.resolve("/health/live"), "live");
            assertHealth(client, baseUri.resolve("/health/ready"), "ready");
            HttpResponse<String> exceptions = get(client, baseUri.resolve("/exceptions"));
            assertThat(exceptions.statusCode()).isEqualTo(200);
            assertJsonContentType(exceptions);
            assertThat(exceptions.headers().firstValue("X-Request-ID")).hasValue("acceptance-request");
            assertThat(exceptions.headers().firstValue("X-Correlation-ID")).hasValue("acceptance-correlation");
            assertThat(exceptions.body()).contains(
                    "\"rowCount\":3",
                    "\"exceptionType\":\"LATE_DELIVERY\"",
                    "\"exceptionType\":\"INSUFFICIENT_QUANTITY\"",
                    "\"exceptionType\":\"PRIORITY_RISK\"",
                    "\"reason\":\"Latest delivery is after the required date plus the configured tolerance.\"",
                    "\"recommendedAction\":\"Arrange the missing quantity or approve an allocation change.\"");

            HttpResponse<String> kotlinOrderSummary = get(client, baseUri.resolve("/kotlin-order-summary"));
            assertThat(kotlinOrderSummary.statusCode()).isEqualTo(200);
            assertJsonContentType(kotlinOrderSummary);
            assertThat(kotlinOrderSummary.body()).contains(
                    "\"rowCount\":3",
                    "\"deliveredQuantity\"",
                    "\"deliveryPercent\"",
                    "\"status\":\"COMPLETE\"");

            String lowerThresholdYaml = yaml.replace("minimumDeliveryRatio: 0.8", "minimumDeliveryRatio: 0.4");
            assertThat(lowerThresholdYaml).contains("minimumDeliveryRatio: 0.4");
            assertChangedThreshold(client, lowerThresholdYaml);
        }
    }

    @Test
    void rejectsUnknownProviderAtLoadWithoutExposingConfiguration() {
        String yaml = configuration().replace("id: demo.orders", "id: missing.orders");
        assertLoadFailure(yaml, PlatformErrorCode.PROVIDER_NOT_FOUND);
    }

    @Test
    void rejectsInvalidJdbcUrlAtLoadWithoutExposingPassword() {
        String yaml = configuration().replace(DATABASE_URL, "jdbc:invalid");
        PlatformException failure = loadFailure(yaml);
        assertThat(failure.code()).isEqualTo(PlatformErrorCode.PROVIDER_CONFIG_ERROR);
        assertThat(failure.getMessage()).doesNotContain("heron");
        assertThat(failure.diagnostics()).allSatisfy(diagnostic -> {
            assertThat(diagnostic.message()).doesNotContain("heron");
            assertThat(diagnostic.remediation()).doesNotContain("heron");
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
    void rejectsUnknownEntrypointTargetAfterBuildingAndCleansUp() {
        String yaml = configuration().replace("target: exception-detector", "target: missing-target");
        assertLoadFailure(yaml, PlatformErrorCode.GRAPH_REFERENCE_ERROR);
    }

    private static void assertChangedThreshold(HttpClient client, String yaml) throws Exception {
        try (HostApplication lowerThresholdApplication = ApplicationLoader.load(stream(yaml));
             HelidonHostAdapter lowerThresholdAdapter = new HelidonHostAdapter()) {
            lowerThresholdAdapter.start(lowerThresholdApplication, HOST_CONFIGURATION);
            HttpResponse<String> response = get(client,
                    URI.create("http://127.0.0.1:" + lowerThresholdAdapter.port() + "/exceptions"));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"rowCount\":1", "\"exceptionType\":\"LATE_DELIVERY\"");
            assertThat(response.body()).doesNotContain("INSUFFICIENT_QUANTITY", "PRIORITY_RISK");
        }
    }

    private static void assertHealth(HttpClient client, URI uri, String status) throws Exception {
        HttpResponse<String> response = get(client, uri);
        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertThat(response.body()).isEqualTo("{\"status\":\"" + status + "\"}");
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("X-Request-ID", "acceptance-request")
                .header("X-Correlation-ID", "acceptance-correlation")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertJsonContentType(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("content-type"))
                .hasValue("application/json; charset=utf-8");
    }

    private static void assertLoadFailure(String yaml, PlatformErrorCode code) {
        PlatformException failure = loadFailure(yaml);
        assertThat(failure.code()).isEqualTo(code);
        assertThat(failure.getMessage()).doesNotContain("heron");
    }

    private static PlatformException loadFailure(String yaml) {
        var yamlStream = stream(yaml);
        PlatformException failure = catchThrowableOfType(PlatformException.class,
                () -> ApplicationLoader.load(yamlStream));
        assertThat(failure).isNotNull();
        return failure;
    }

    private static String configuration() {
        try (InputStream input = FactoryDemoAcceptanceTest.class.getResourceAsStream(CONFIGURATION_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("factory-demo configuration resource is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("factory-demo configuration could not be read", failure);
        }
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
