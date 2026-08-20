package dev.hogwai.platform.spi.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlatformEventTest {

    private static final ProviderId PROVIDER = new ProviderId("acme");
    private static final ProviderVersion VERSION = ProviderVersion.parse("1.0.0");

    private static PlatformEvent event() {
        return new PlatformEvent(PlatformEvent.SCHEMA_VERSION, PlatformEventType.CAPABILITY_COMPLETED,
                PROVIDER, VERSION, "cap-1", "snap-1", "req-1",
                PlatformEvent.EventStatus.COMPLETED, Duration.ofMillis(5), 10L, 100L, null);
    }

    @Test
    void exposesMetadata() {
        PlatformEvent event = event();
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.type()).isEqualTo(PlatformEventType.CAPABILITY_COMPLETED);
        assertThat(event.providerId()).isEqualTo(PROVIDER);
        assertThat(event.providerVersion()).isEqualTo(VERSION);
        assertThat(event.capabilityId()).isEqualTo("cap-1");
        assertThat(event.snapshotId()).isEqualTo("snap-1");
        assertThat(event.requestId()).isEqualTo("req-1");
        assertThat(event.status()).isEqualTo(PlatformEvent.EventStatus.COMPLETED);
        assertThat(event.duration()).isEqualTo(Duration.ofMillis(5));
        assertThat(event.datasetRowCount()).isEqualTo(10L);
        assertThat(event.datasetSizeBytes()).isEqualTo(100L);
        assertThat(event.failureCode()).isNull();
    }

    @Test
    void schemaVersionIsOne() {
        assertThat(PlatformEvent.SCHEMA_VERSION).isEqualTo(1);
    }

    @Test
    void exposesOnlyDocumentedRecordComponents() {
        // Structural guard: the record must expose exactly the documented
        // components, in order, with the exact types. Any future record, raw
        // config, secret or payload component fails this assertion.
        RecordComponent[] components = PlatformEvent.class.getRecordComponents();
        assertThat(components).extracting(RecordComponent::getName).containsExactly(
                "schemaVersion", "type", "providerId", "providerVersion", "capabilityId",
                "snapshotId", "requestId", "status", "duration", "datasetRowCount",
                "datasetSizeBytes", "failureCode");
        assertThat(components).extracting(c -> c.getType().getName()).containsExactly(
                int.class.getName(), PlatformEventType.class.getName(), ProviderId.class.getName(),
                ProviderVersion.class.getName(), String.class.getName(), String.class.getName(),
                String.class.getName(), PlatformEvent.EventStatus.class.getName(), Duration.class.getName(),
                Long.class.getName(), Long.class.getName(), String.class.getName());
    }

    @Test
    void rejectsNegativeDuration() {
        Duration negativeDuration = Duration.ofMillis(-1);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.CAPABILITY_COMPLETED, PROVIDER, VERSION,
                "cap", "snap", "req", PlatformEvent.EventStatus.COMPLETED, negativeDuration, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertThatThrownBy(() -> new PlatformEvent(2, PlatformEventType.SNAPSHOT_BUILT,
                PROVIDER, VERSION, "cap", "snap", "req", PlatformEvent.EventStatus.STARTED,
                Duration.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new PlatformEvent(1, null, PROVIDER, VERSION, "cap", "snap", "req",
                PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, null, VERSION,
                "cap", "snap", "req", PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, PROVIDER, null,
                "cap", "snap", "req", PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, PROVIDER, VERSION,
                "cap", "snap", "req", null, Duration.ZERO, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, PROVIDER, VERSION,
                "cap", "snap", "req", PlatformEvent.EventStatus.STARTED, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, PROVIDER, VERSION,
                "", "snap", "req", PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, PROVIDER, VERSION,
                "cap", " ", "req", PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.SNAPSHOT_BUILT, PROVIDER, VERSION,
                "cap", "snap", "", PlatformEvent.EventStatus.STARTED, Duration.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeDatasetSizes() {
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.CAPABILITY_COMPLETED, PROVIDER, VERSION,
                "cap", "snap", "req", PlatformEvent.EventStatus.COMPLETED, Duration.ZERO, -1L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.CAPABILITY_COMPLETED, PROVIDER, VERSION,
                "cap", "snap", "req", PlatformEvent.EventStatus.COMPLETED, Duration.ZERO, null, -1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankFailureCode() {
        assertThatThrownBy(() -> new PlatformEvent(1, PlatformEventType.CAPABILITY_FAILED, PROVIDER, VERSION,
                "cap", "snap", "req", PlatformEvent.EventStatus.FAILED, Duration.ZERO, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventTypeExposesAllTypes() {
        assertThat(PlatformEventType.values()).containsExactly(
                PlatformEventType.SNAPSHOT_BUILT,
                PlatformEventType.SNAPSHOT_ACTIVATED,
                PlatformEventType.RELOAD_REJECTED,
                PlatformEventType.SNAPSHOT_RETIRED,
                PlatformEventType.CAPABILITY_STARTED,
                PlatformEventType.CAPABILITY_COMPLETED,
                PlatformEventType.CAPABILITY_FAILED);
    }
}
