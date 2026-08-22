package dev.hogwai.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CreateProviderCommandTest {

    @TempDir
    Path tempDirectory;

    @Test
    void scaffoldsASourceProvider() throws Exception {
        CreateProviderCommand command = command("orders", "com.acme.orders", "SOURCE");

        assertThat(command.call()).isZero();

        Path project = tempDirectory.resolve("orders");
        assertThat(project.resolve("settings.gradle.kts")).exists();
        assertThat(project.resolve("build.gradle.kts")).exists();
        Path source = project.resolve("src/main/java/com/acme/orders/OrdersProviderFactory.java");
        assertThat(source).exists();
        assertThat(Files.readString(buildGradleKts(project)))
                .contains("dev.hogwai.platform:heron-platform-spi:0.1.0")
                .contains("annotationProcessor(\"dev.hogwai.platform:heron-platform-processor:0.1.0\")");
        assertThat(project.resolve("src/test/java/com/acme/orders/OrdersProviderFactoryTest.java")).exists();
        assertThat(Files.readString(source)).contains("@HeronService(value = ProviderFactory.class")
                .contains("id = \"com.acme.orders.orders\"");
        assertThat(project.resolve("src/main/resources/META-INF/services")).doesNotExist();
    }

    @Test
    void scaffoldsATransformProviderWithAnInputPort() throws Exception {
        CreateProviderCommand command = command("normalizer", "com.acme.normalizer", "TRANSFORM");

        assertThat(command.call()).isZero();

        String source = Files.readString(tempDirectory.resolve(
                "normalizer/src/main/java/com/acme/normalizer/NormalizerProviderFactory.java"));
        assertThat(source)
                .contains("CapabilityKind.TRANSFORM")
                .contains("new PortId(\"input\")");
    }

    @Test
    void scaffoldsAKotlinSourceProvider() throws Exception {
        CreateProviderCommand command = command("orders", "com.acme.orders", "SOURCE", "KOTLIN");

        assertThat(command.call()).isZero();

        Path project = tempDirectory.resolve("orders");
        assertThat(project.resolve("src/main/kotlin/com/acme/orders/OrdersProviderFactory.kt")).exists();
        assertThat(project.resolve("src/main/java/com/acme/orders/OrdersProviderFactory.java")).doesNotExist();
        assertThat(Files.readString(buildGradleKts(project)))
                .contains("kotlin(\"jvm\") version")
                .contains("kotlin(\"kapt\") version")
                .contains("dev.hogwai.platform:heron-platform-spi:0.1.0")
                .contains("kapt(\"dev.hogwai.platform:heron-platform-processor:0.1.0\")");
        assertThat(Files.readString(project.resolve("src/main/kotlin/com/acme/orders/OrdersProviderFactory.kt")))
                .contains("@HeronService(value = ProviderFactory::class")
                .contains("id = \"com.acme.orders.orders\"");
        assertThat(project.resolve("src/main/resources/META-INF/services")).doesNotExist();
    }

    @Test
    void scaffoldsAKotlinTransformProviderWithAnInputPort() throws Exception {
        CreateProviderCommand command = command("normalizer", "com.acme.normalizer", "TRANSFORM", "KOTLIN");

        assertThat(command.call()).isZero();

        String source = Files.readString(tempDirectory.resolve(
                "normalizer/src/main/kotlin/com/acme/normalizer/NormalizerProviderFactory.kt"));
        assertThat(source).contains("CapabilityKind.TRANSFORM")
                .contains("PortId(\"input\") to PortDescriptor");
    }

    @Test
    void rejectsAnUnknownProviderKind() {
        CreateProviderCommand command = command("orders", "com.acme.orders", "UNKNOWN");

        assertThat(command.call()).isOne();
        assertThat(tempDirectory.resolve("orders")).doesNotExist();
    }

    @Test
    void rejectsAnUnknownProviderLanguage() {
        CreateProviderCommand command = command("orders", "com.acme.orders", "SOURCE", "RUST");

        assertThat(command.call()).isOne();
        assertThat(tempDirectory.resolve("orders")).doesNotExist();
    }

    private CreateProviderCommand command(String name, String packageName, String kind) {
        return command(name, packageName, kind, null);
    }

    private static Path buildGradleKts(Path project) {
        return project.resolve("build.gradle.kts");
    }

    private CreateProviderCommand command(String name, String packageName, String kind, String language) {
        CreateProviderCommand command = new CreateProviderCommand(tempDirectory);
        command.name = name;
        command.packageName = packageName;
        command.kind = kind;
        command.language = language;
        return command;
    }
}
