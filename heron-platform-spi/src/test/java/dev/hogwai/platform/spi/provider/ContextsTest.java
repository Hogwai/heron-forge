package dev.hogwai.platform.spi.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContextsTest {

    private static final ProviderId PROVIDER = new ProviderId("acme");
    private static final ProviderVersion VERSION = ProviderVersion.parse("1.0.0");

    @Test
    void validationContextExposesProviderIdentity() {
        ValidationContext context = new ValidationContext(PROVIDER, VERSION);
        assertThat(context.providerId()).isEqualTo(PROVIDER);
        assertThat(context.version()).isEqualTo(VERSION);
    }

    @Test
    void validationContextRejectsNullArguments() {
        assertThatThrownBy(() -> new ValidationContext(null, VERSION)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidationContext(PROVIDER, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildContextExposesIntendedData() {
        Clock clock = Clock.systemUTC();
        ResourceTracker tracker = resource -> { };
        BuildContext context = new BuildContext("app", "snap", clock, tracker);
        assertThat(context.applicationId()).isEqualTo("app");
        assertThat(context.snapshotId()).isEqualTo("snap");
        assertThat(context.clock()).isSameAs(clock);
        assertThat(context.resourceTracker()).isSameAs(tracker);
    }

    @Test
    void buildContextRejectsBlankIdentifiers() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new BuildContext("", "snap", clock, r -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuildContext("app", " ", clock, r -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildContextRejectsNullArguments() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new BuildContext(null, "snap", clock, r -> { }))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuildContext("app", null, clock, r -> { }))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuildContext("app", "snap", null, r -> { }))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BuildContext("app", "snap", clock, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildContextExposesOnlyDocumentedApi() {
        // Structural guard: exactly one public constructor, no public fields and
        // exactly the documented zero-arg accessors. Any added logger, metrics,
        // event sink, secret String or raw payload member fails this assertion.
        ApiAssert.assertPublicApi(BuildContext.class,
                new Class<?>[]{String.class, String.class, Clock.class, ResourceTracker.class},
                Set.of(
                        ApiAssert.MethodSpec.of("applicationId", String.class),
                        ApiAssert.MethodSpec.of("snapshotId", String.class),
                        ApiAssert.MethodSpec.of("clock", Clock.class),
                        ApiAssert.MethodSpec.of("resourceTracker", ResourceTracker.class)));
    }

    @Test
    void validationContextExposesOnlyDocumentedApi() {
        ApiAssert.assertPublicApi(ValidationContext.class,
                new Class<?>[]{ProviderId.class, ProviderVersion.class},
                Set.of(
                        ApiAssert.MethodSpec.of("providerId", ProviderId.class),
                        ApiAssert.MethodSpec.of("version", ProviderVersion.class)));
    }
}
