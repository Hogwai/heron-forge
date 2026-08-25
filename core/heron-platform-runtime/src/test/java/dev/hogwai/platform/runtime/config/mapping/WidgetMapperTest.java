package dev.hogwai.platform.runtime.config.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.hogwai.platform.runtime.config.WidgetConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import org.junit.jupiter.api.Test;

class WidgetMapperTest {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private record Mapped(List<WidgetConfig> widgets, List<Diagnostic> diagnostics) {
    }

    private Mapped map(String yaml) throws Exception {
        JsonNode node = mapper.readTree(yaml);
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<WidgetConfig> widgets = WidgetMapper.mapWidgets(node.get("widgets"), "/widgets", diagnostics);
        return new Mapped(widgets, diagnostics);
    }

    @Test
    void absentSectionMapsToEmptyList() throws Exception {
        Mapped result = map("apiVersion: heron.dev/v1\napplication: demo\n");
        assertThat(result.widgets()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void mapsDeclarationsInOrder() throws Exception {
        Mapped result = map("""
                widgets:
                  - id: exceptions-kpi
                    type: kpi
                    title: Exceptions ouvertes
                    target: supply-chain-exceptions
                  - id: ratio-chart
                    type: chart
                    title: Ratio de livraison
                    target: kotlin-order-summary
                """);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.widgets()).containsExactly(
                new WidgetConfig("exceptions-kpi", "kpi", "Exceptions ouvertes", "supply-chain-exceptions"),
                new WidgetConfig("ratio-chart", "chart", "Ratio de livraison", "kotlin-order-summary"));
    }

    @Test
    void rejectsNonListSection() throws Exception {
        Mapped result = map("widgets: nope\n");
        assertThat(result.widgets()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(PlatformErrorCode.CONFIG_SCHEMA_ERROR);
            assertThat(diagnostic.path()).isEqualTo("/widgets");
        });
    }

    @Test
    void reportsUnknownFieldWithGenericPathAndStillMaps() throws Exception {
        Mapped result = map("""
                widgets:
                  - id: w1
                    type: kpi
                    title: T
                    target: e1
                    color: red
                """);
        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code() == PlatformErrorCode.CONFIG_SCHEMA_ERROR
                        && diagnostic.path().equals("/widgets/0/<key>"));
        assertThat(result.widgets()).containsExactly(new WidgetConfig("w1", "kpi", "T", "e1"));
    }

    @Test
    void rejectsMissingTitle() throws Exception {
        Mapped result = map("""
                widgets:
                  - id: w1
                    type: kpi
                    target: e1
                """);
        assertThat(result.widgets()).isEmpty();
        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code() == PlatformErrorCode.CONFIG_SCHEMA_ERROR
                        && diagnostic.path().equals("/widgets/0/title"));
    }

    @Test
    void rejectsUnknownType() throws Exception {
        Mapped result = map("""
                widgets:
                  - id: w1
                    type: gauge
                    title: T
                    target: e1
                """);
        assertThat(result.widgets()).isEmpty();
        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code() == PlatformErrorCode.CONFIG_SCHEMA_ERROR
                        && diagnostic.path().equals("/widgets/0/type"));
    }

    @Test
    void rejectsDuplicateId() throws Exception {
        Mapped result = map("""
                widgets:
                  - id: w1
                    type: kpi
                    title: A
                    target: e1
                  - id: w1
                    type: table
                    title: B
                    target: e2
                """);
        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code() == PlatformErrorCode.CONFIG_SCHEMA_ERROR
                        && diagnostic.path().equals("/widgets/1/id"));
    }
}
