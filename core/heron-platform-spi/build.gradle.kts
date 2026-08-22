plugins {
    `java-library`
    `maven-publish`
}

// The Revapi Gradle plugin is resolved from the root buildscript classpath
// (see root build.gradle.kts) so that the analyzer version is controlled by
// `revapiVersion` in gradle.properties.
apply(plugin = "org.revapi.revapi-gradle-plugin")

val platformSpiBaselineVersion = providers.gradleProperty("platformSpiBaselineVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()

dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}

publishing {
    publications {
        create<MavenPublication>("baseline") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.name
            version = platformSpiBaselineVersion
        }
    }
}

// Revapi API compatibility check against an explicit baseline artifact.
// The POC publishes the baseline to Maven Local before running the check.
configure<org.revapi.gradle.RevapiExtension> {
    oldGroup = "dev.hogwai.platform"
    oldName = "heron-platform-spi"
    setOldVersion(platformSpiBaselineVersion)
}

// Coverage gate: at least 80% instruction coverage for the SPI module.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
