package dev.hogwai.platform.runtime.load;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import dev.hogwai.platform.runtime.config.EntrypointConfig;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.runtime.config.WidgetConfig;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;

/**
 * Resolves widget declarations into export-ready descriptors.
 *
 * <p>Build-time surface for tooling (the CLI {@code widgets export} command):
 * parses and structurally validates the configuration, then resolves every
 * widget target to its endpoint path. Provider compilation is intentionally
 * not run here; full validation remains the boot/register path.
 */
public final class WidgetExports {

    /**
     * One export-ready widget descriptor.
     *
     * @param id    stable widget identifier
     * @param type  widget type (kpi, table, chart)
     * @param title display title
     * @param path  resolved absolute endpoint path
     */
    public record ResolvedWidget(String id, String type, String title, String path) {
    }

    /**
     * Export result for one configuration.
     *
     * @param applicationName the declared application name
     * @param widgets         the resolved widgets in declaration order
     */
    public record Export(String applicationName, List<ResolvedWidget> widgets) {
    }

    private WidgetExports() {
        // no instances
    }

    /**
     * Parses the configuration and resolves its widgets against declared endpoints.
     *
     * @param yaml YAML configuration input
     * @return the export result
     * @throws PlatformException when the configuration is invalid or a widget
     *                           target matches no declared endpoint
     */
    public static Export export(InputStream yaml) {
        Objects.requireNonNull(yaml, "yaml must not be null");
        ParsedApplication parsed = new SafeYamlParser().parse(yaml);
        if (!parsed.isValid()) {
            throw new PlatformException(firstErrorCode(parsed), parsed.diagnostics());
        }
        List<EntrypointConfig> entrypoints = parsed.application().entrypoints();
        List<ResolvedWidget> widgets = parsed.application().widgets().stream()
                .map(widget -> new ResolvedWidget(widget.id(), widget.type(), widget.title(),
                        pathOf(entrypoints, widget)))
                .toList();
        return new Export(parsed.application().name(), widgets);
    }

    private static String pathOf(List<EntrypointConfig> entrypoints, WidgetConfig widget) {
        return entrypoints.stream()
                .filter(entrypoint -> entrypoint.id().equals(widget.target()))
                .map(EntrypointConfig::path)
                .findFirst()
                .orElseThrow(() -> new PlatformException(PlatformErrorCode.GRAPH_REFERENCE_ERROR,
                        List.of(new Diagnostic(PlatformErrorCode.GRAPH_REFERENCE_ERROR, Severity.ERROR,
                                "/widgets", "widget target does not match any declared endpoint",
                                "reference an existing endpoint id"))));
    }

    private static PlatformErrorCode firstErrorCode(ParsedApplication parsed) {
        return parsed.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                .map(Diagnostic::code)
                .findFirst()
                .orElse(PlatformErrorCode.CONFIG_PARSE_ERROR);
    }
}
