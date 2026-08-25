package dev.hogwai.platform.runtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import org.junit.jupiter.api.Test;

class WidgetSurfaceValidationTest {

    private static final String YAML = """
            apiVersion: heron.dev/v1
            application: widget-demo
            capabilities:
              - id: orders
                provider:
                  id: no.such.provider
                  version: 1.0.0
            endpoints:
              - id: orders-endpoint
                method: GET
                path: /orders
                target: orders
            widgets:
              - id: orders-kpi
                type: kpi
                title: Orders
                target: orders-endpoint
              - id: broken-kpi
                type: kpi
                title: Broken
                target: missing-endpoint
            """;

    @Test
    void validateAggregatesWidgetTargetDiagnostics() {
        var report = ApplicationLoader.validate(stream(YAML));
        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code() == PlatformErrorCode.GRAPH_REFERENCE_ERROR
                        && diagnostic.path().equals("/widgets/1/target"));
        assertThat(report.diagnostics()).noneMatch(diagnostic ->
                diagnostic.path() != null && diagnostic.path().startsWith("/widgets/0"));
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
