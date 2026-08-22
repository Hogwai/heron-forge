package dev.hogwai.platform.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Loads and renders templates embedded in the CLI distribution. */
final class TemplateRenderer {

    private TemplateRenderer() {
        // utility class
    }

    static String load(String name) throws IOException {
        String resource = name.startsWith("/") ? name : "/templates/" + name;
        try (InputStream input = TemplateRenderer.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("template not found: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String render(String template, Map<String, String> model) {
        String rendered = template;
        for (Map.Entry<String, String> entry : model.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }
}
