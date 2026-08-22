plugins {
    `java-library`
    `maven-publish`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

val platformSpiBaselineVersion = providers.gradleProperty("platformSpiBaselineVersion").get()

dependencies {
    implementation(project(":core:heron-platform-spi"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        // Mirrors the SPI baseline publication so projects scaffolded with
        // `heron create` resolve the processor from mavenLocal at the same
        // version as the SPI baseline.
        create<MavenPublication>("baseline") {
            from(components["java"])
            artifactId = project.name
            version = platformSpiBaselineVersion
        }
    }
}
