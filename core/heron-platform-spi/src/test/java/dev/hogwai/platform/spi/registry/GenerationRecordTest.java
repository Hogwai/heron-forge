package dev.hogwai.platform.spi.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationRecordTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private static GenerationRecord generateRecord(GenerationStatus status) {
        return new GenerationRecord("app", "gen-1", "sha256", "raw: yaml", status, NOW, "cli");
    }

    @Test
    void acceptsValidRecordAndExposesComponents() {
        GenerationRecord generationRecord = generateRecord(GenerationStatus.EXPERIMENTAL);

        assertThat(generationRecord.applicationId()).isEqualTo("app");
        assertThat(generationRecord.generationId()).isEqualTo("gen-1");
        assertThat(generationRecord.configSha256()).isEqualTo("sha256");
        assertThat(generationRecord.rawYaml()).isEqualTo("raw: yaml");
        assertThat(generationRecord.status()).isEqualTo(GenerationStatus.EXPERIMENTAL);
        assertThat(generationRecord.createdAt()).isEqualTo(NOW);
        assertThat(generationRecord.createdBy()).isEqualTo("cli");
    }

    @Test
    void hasValueEquality() {
        assertThat(generateRecord(GenerationStatus.STABLE)).isEqualTo(generateRecord(GenerationStatus.STABLE));
        assertThat(generateRecord(GenerationStatus.STABLE)).hasSameHashCodeAs(generateRecord(GenerationStatus.STABLE));
        assertThat(generateRecord(GenerationStatus.STABLE)).isNotEqualTo(generateRecord(GenerationStatus.EXPERIMENTAL));
        assertThat(new GenerationRecord("app", "gen-2", "sha256", "raw: yaml",
                GenerationStatus.STABLE, NOW, "cli")).isNotEqualTo(generateRecord(GenerationStatus.STABLE));
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new GenerationRecord(null, "gen", "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("applicationId must not be null");
        assertThatThrownBy(() -> new GenerationRecord("app", null, "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("generationId must not be null");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", null, "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configSha256 must not be null");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "sha", null,
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rawYaml must not be null");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "sha", "yaml",
                null, NOW, "cli"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, null, "cli"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdAt must not be null");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdBy must not be null");
    }

    @Test
    void rejectsBlankStrings() {
        assertThatThrownBy(() -> new GenerationRecord(" ", "gen", "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("applicationId must not be blank");
        assertThatThrownBy(() -> new GenerationRecord("app", "", "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("generationId must not be blank");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "  ", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("configSha256 must not be blank");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "sha", "\t",
                GenerationStatus.EXPERIMENTAL, NOW, "cli"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rawYaml must not be blank");
        assertThatThrownBy(() -> new GenerationRecord("app", "gen", "sha", "yaml",
                GenerationStatus.EXPERIMENTAL, NOW, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("createdBy must not be blank");
    }
}
