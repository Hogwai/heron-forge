package dev.hogwai.platform.spi;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifies the SPI stays a pure, framework-independent contract module.
 */
@AnalyzeClasses(packages = "dev.hogwai.platform.spi", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule spiDoesNotDependOnFrameworksOrOtherModules =
            noClasses().that().resideInAPackage("dev.hogwai.platform.spi..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.helidon..", "io.vertx..", "io.quarkus..", "io.micronaut..",
                            "jakarta.servlet..", "org.springframework..",
                            "picocli..", "org.jdbi..", "org.postgresql..",
                            "com.fasterxml.jackson..", "org.slf4j..",
                            "dev.hogwai.platform.runtime..", "dev.hogwai.platform.data..",
                            "dev.hogwai.platform.host.helidon..", "dev.hogwai.platform.cli..",
                            "dev.hogwai.platform.examples..");
}