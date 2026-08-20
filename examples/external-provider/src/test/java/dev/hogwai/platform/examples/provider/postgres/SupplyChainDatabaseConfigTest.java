package dev.hogwai.platform.examples.provider.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplyChainDatabaseConfigTest {

    @Test
    void exposesTheDatabaseConfigurationSchema() {
        assertThat(SupplyChainDatabaseConfig.databaseConfigSchema().allowedFields())
                .containsExactlyInAnyOrder("url", "user", "password");
        assertThat(SupplyChainDatabaseConfig.databaseConfigSchema().requiredFields()).isEmpty();
    }

    @Test
    void resolvesExplicitValuesWithoutChangingThem() {
        DataAccessConfiguration config = SupplyChainDatabaseConfig.from(Map.of(
                "url", "jdbc:postgresql://db/example", "user", "db-user", "password", "db-password"));

        assertThat(config.url()).isEqualTo("jdbc:postgresql://db/example");
        assertThat(config.username()).isEqualTo("db-user");
        assertThat(config.password()).isEqualTo("db-password");
    }

    @Test
    void resolvesDefaultsAndEnvironmentOverrides() {
        DataAccessConfiguration config = SupplyChainDatabaseConfig.from(Map.of());

        assertThat(config.url()).isEqualTo(environmentOrDefault(
                "HERON_DB_URL", "jdbc:postgresql://localhost:5432/heron_demo"));
        assertThat(config.username()).isEqualTo(environmentOrDefault("HERON_DB_USER", "heron"));
        assertThat(config.password()).isEqualTo(environmentOrDefault("HERON_DB_PASSWORD", "heron"));
    }

    @Test
    void rejectsUnknownAndBlankDatabaseFields() {
        List<dev.hogwai.platform.spi.Diagnostic> diagnostics = SupplyChainDatabaseConfig.validate(
                Map.of("url", " ", "unexpected", "value"), "orders");

        assertThat(diagnostics).hasSize(2);
        assertThat(diagnostics).extracting(dev.hogwai.platform.spi.Diagnostic::message)
                .containsExactlyInAnyOrder(
                        "unknown database configuration field",
                        "database configuration field must be a non-blank string");
    }

    @Test
    void rejectsNullConfigurationWithProviderSpecificDiagnostic() {
        assertThat(SupplyChainDatabaseConfig.validate(null, "deliveries"))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("deliveries configuration must be an object"));
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
