package dev.hogwai.platform.data.postgres;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifies the PostgreSQL data brick only depends on the SPI data contract.
 */
@AnalyzeClasses(packages = "dev.hogwai.platform.data", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dataBrickDoesNotDependOnCoreOrOtherBricks =
            noClasses().that().resideInAPackage("dev.hogwai.platform.data..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.hogwai.platform.runtime..",
                            "dev.hogwai.platform.host.helidon..",
                            "dev.hogwai.platform.cli..",
                            "dev.hogwai.platform.examples..");
}