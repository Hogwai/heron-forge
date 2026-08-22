package dev.hogwai.platform.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectTest {
    public static final String VERSION_0_0_0 = "0.0.0";
    public static final String VERSION_1_2_3 = "1.2.3";
    public static final String ORDERS = "orders";

    // ------------------------------------------------------------------
    // PortId
    // ------------------------------------------------------------------

    @Test
    void portIdAcceptsValidValue() {
        PortId id = new PortId(ORDERS);
        assertThat(id.value()).isEqualTo(ORDERS);
        assertThat(id).hasToString(ORDERS);
        assertThat(new PortId(ORDERS)).isEqualTo(new PortId(ORDERS));
        assertThat(new PortId(ORDERS)).hasSameHashCodeAs(new PortId(ORDERS));
    }

    @Test
    void portIdRejectsNull() {
        assertThatThrownBy(() -> new PortId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void portIdRejectsBlank() {
        assertThatThrownBy(() -> new PortId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortId("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void portIdRejectsWhitespace() {
        assertThatThrownBy(() -> new PortId("my port")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortId("my\tport")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortId("my\nport")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortId("my\u00A0port")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortId("my\u2007port")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortId("my\u202Fport")).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // ProviderId
    // ------------------------------------------------------------------

    @Test
    void providerIdAcceptsValidValue() {
        ProviderId id = new ProviderId("acme");
        assertThat(id.value()).isEqualTo("acme");
        assertThat(id).hasToString("acme");
        assertThat(new ProviderId("acme")).isEqualTo(new ProviderId("acme"));
    }

    @Test
    void providerIdRejectsNull() {
        assertThatThrownBy(() -> new ProviderId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void providerIdRejectsBlank() {
        assertThatThrownBy(() -> new ProviderId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderId("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerIdRejectsWhitespace() {
        assertThatThrownBy(() -> new ProviderId("ac me")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderId("ac\tme")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderId("ac\u00A0me")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderId("ac\u2007me")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderId("ac\u202Fme")).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // ProviderVersion
    // ------------------------------------------------------------------

    @Test
    void providerVersionParsesExactCanonicalVersions() {
        assertThat(ProviderVersion.parse(VERSION_1_2_3)).isEqualTo(new ProviderVersion(1, 2, 3));
        assertThat(ProviderVersion.parse(VERSION_0_0_0)).isEqualTo(new ProviderVersion(0, 0, 0));
        assertThat(ProviderVersion.parse("10.20.30")).isEqualTo(new ProviderVersion(10, 20, 30));
        assertThat(ProviderVersion.parse(VERSION_1_2_3).major()).isEqualTo(1);
        assertThat(ProviderVersion.parse(VERSION_1_2_3).minor()).isEqualTo(2);
        assertThat(ProviderVersion.parse(VERSION_1_2_3).patch()).isEqualTo(3);
    }

    @Test
    void providerVersionToStringIsCanonicalAndRoundTrips() {
        ProviderVersion version = ProviderVersion.parse(VERSION_1_2_3);
        assertThat(version).hasToString(VERSION_1_2_3);
        assertThat(ProviderVersion.parse(version.toString())).isEqualTo(version);
        assertThat(ProviderVersion.parse(VERSION_0_0_0)).hasToString(VERSION_0_0_0);
    }

    @Test
    void providerVersionRejectsNullAndBlank() {
        assertThatThrownBy(() -> ProviderVersion.parse(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerVersionRejectsNonCanonicalShapes() {
        assertThatThrownBy(() -> ProviderVersion.parse("1.2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.2.3.4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("v1.2.3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.2.3-alpha")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.2.3+build")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.2.3 ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse(" 1.2.3")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerVersionRejectsLeadingZerosAsNonCanonical() {
        assertThatThrownBy(() -> ProviderVersion.parse("01.2.3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.02.3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.2.03")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerVersionRejectsNegativeComponents() {
        assertThatThrownBy(() -> ProviderVersion.parse("1.-2.3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderVersion.parse("1.2.-3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderVersion(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderVersion(0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderVersion(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
