package dev.hogwai.platform.runtime;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Verifies the runtime stays framework-independent and only depends on the SPI. */
@AnalyzeClasses(packages = "dev.hogwai.platform.runtime", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule runtimeDoesNotDependOnFrameworksOrBricks =
            noClasses().that().resideInAPackage("dev.hogwai.platform.runtime..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.helidon..", "picocli..", "org.jdbi..", "org.postgresql..",
                            "dev.hogwai.platform.host.helidon..", "dev.hogwai.platform.cli..",
                            "dev.hogwai.platform.data..", "dev.hogwai.platform.examples..");
}