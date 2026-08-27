package dev.hogwai.platform.cli;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifies the CLI module dependency boundaries.
 */
@AnalyzeClasses(packages = "dev.hogwai.platform.cli", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule cliDoesNotDependOnBricksDirectly =
            noClasses().that().resideInAPackage("dev.hogwai.platform.cli..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.hogwai.platform.host.helidon..",
                            "dev.hogwai.platform.data..");
}