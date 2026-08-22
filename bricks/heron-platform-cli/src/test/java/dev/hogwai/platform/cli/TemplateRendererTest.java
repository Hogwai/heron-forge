package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void rendersKnownPlaceholders() {
        assertThat(TemplateRenderer.render("Hello {{name}}", Map.of("name", "world")))
                .isEqualTo("Hello world");
    }

    @Test
    void leavesUnknownPlaceholdersUntouched() {
        assertThat(TemplateRenderer.render("{{known}} {{unknown}}", Map.of("known", "value")))
                .isEqualTo("value {{unknown}}");
    }

    @Test
    void loadsExistingTemplateFromClasspath() throws Exception {
        assertThat(TemplateRenderer.load("settings.gradle.kts.template"))
                .contains("rootProject.name");
    }
}
