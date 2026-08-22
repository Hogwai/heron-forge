package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectNamesTest {

    @Test
    void acceptsLowercaseProjectNames() {
        assertThat(ProjectNames.validateProjectName("orders-api")).isEqualTo("orders-api");
    }

    @Test
    void rejectsNamesThatCannotBeUsedAsProjectDirectories() {
        assertThatThrownBy(() -> ProjectNames.validateProjectName("Orders"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectNames.validateProjectName("123-api"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectNames.validateProjectName("class"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivesValidPackageAndTypeNames() {
        assertThat(ProjectNames.derivePackage("orders-api")).isEqualTo("ordersapi");
        assertThat(ProjectNames.toJavaTypeName("orders-api")).isEqualTo("OrdersApi");
    }

    @Test
    void rejectsInvalidOrKeywordPackageSegments() {
        assertThatThrownBy(() -> ProjectNames.validatePackage("com.int.orders"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectNames.validatePackage("123.orders"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
