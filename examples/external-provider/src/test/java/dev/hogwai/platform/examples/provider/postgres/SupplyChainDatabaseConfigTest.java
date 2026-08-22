package dev.hogwai.platform.examples.provider.postgres;

import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyChainDatabaseConfigTest {

    @Test
    void exposesTheDatabaseConfigurationSchema() {
        assertThat(SupplyChainDatabaseConfig.databaseConfigSchema().allowedFields())
                .containsExactlyInAnyOrder("url", "user", "password", "maxRows", "maxBytes");
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
    void requiresUrlUserAndPasswordWithoutDefaults() {
        DataAccessConfiguration config = SupplyChainDatabaseConfig.from(Map.of(
                "url", "jdbc:postgresql://db/example",
                "user", "db-user",
                "password", "db-password"));

        assertThat(config.url()).isEqualTo("jdbc:postgresql://db/example");
        assertThat(config.username()).isEqualTo("db-user");
        assertThat(config.password()).isEqualTo("db-password");

        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                IllegalArgumentException.class, () -> SupplyChainDatabaseConfig.from(Map.of())))
                .isNotNull();
        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                IllegalArgumentException.class,
                () -> SupplyChainDatabaseConfig.from(Map.of("user", "u", "password", "p"))))
                .isNotNull();
    }

    @Test
    void rejectsUnknownBlankAndMissingDatabaseFields() {
        List<dev.hogwai.platform.spi.Diagnostic> diagnostics = SupplyChainDatabaseConfig.validate(
                Map.of("url", " ", "unexpected", "value"), "orders");

        assertThat(diagnostics).hasSize(4);
        assertThat(diagnostics).extracting(dev.hogwai.platform.spi.Diagnostic::path,
                        dev.hogwai.platform.spi.Diagnostic::message)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("/config/url",
                                "database configuration field must be a non-blank string"),
                        org.assertj.core.groups.Tuple.tuple("/config/<key>",
                                "unknown database configuration field"),
                        org.assertj.core.groups.Tuple.tuple("/config/user",
                                "missing required database configuration field"),
                        org.assertj.core.groups.Tuple.tuple("/config/password",
                                "missing required database configuration field"));
    }

    @Test
    void rejectsNullConfigurationWithProviderSpecificDiagnostic() {
        assertThat(SupplyChainDatabaseConfig.validate(null, "deliveries"))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("deliveries configuration must be an object"));
    }
}
