package dev.hogwai.platform.runtime.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.junit.jupiter.api.Test;

class WidgetExportsTest {

    private static final String VALID_YAML = """
            apiVersion: heron.dev/v1
            application: widget-demo
            capabilities:
              - id: orders
                provider:
                  id: fixture.provider
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
            """;

    @Test
    void resolvesWidgetsAgainstEndpointPaths() {
        var export = WidgetExports.export(stream(VALID_YAML));
        assertThat(export.applicationName()).isEqualTo("widget-demo");
        assertThat(export.widgets()).containsExactly(
                new WidgetExports.ResolvedWidget("orders-kpi", "kpi", "Orders", "/orders"));
    }

    @Test
    void rejectsInvalidConfiguration() {
        var stream = stream("apiVersion: heron.dev/v1\n");
        assertThatThrownBy(() -> WidgetExports.export(stream))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo(PlatformErrorCode.CONFIG_SCHEMA_ERROR);
    }

    @Test
    void rejectsWidgetTargetWithoutEndpoint() {
        var stream = stream(VALID_YAML.replace("target: orders-endpoint", "target: ghost"));
        assertThatThrownBy(() -> WidgetExports.export(stream))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo(PlatformErrorCode.GRAPH_REFERENCE_ERROR);
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
