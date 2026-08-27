package dev.hogwai.platform.host.helidon;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifies the Helidon host brick only crosses the host contract boundary.
 */
@AnalyzeClasses(packages = "dev.hogwai.platform.host.helidon", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hostDoesNotCrossTheHostBoundary =
            noClasses().that().resideInAPackage("dev.hogwai.platform.host.helidon..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.hogwai.platform.spi.data..",
                            "dev.hogwai.platform.spi.provider..");
}