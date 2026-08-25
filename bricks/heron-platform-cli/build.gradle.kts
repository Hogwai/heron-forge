import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
    `maven-publish`
    application
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

application {
    mainClass.set("dev.hogwai.platform.cli.HeronLauncher")
    applicationName = "heron"
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()
val picocliVersion = providers.gradleProperty("picocliVersion").get()
val jacksonVersion = providers.gradleProperty("jacksonVersion").get()
val junitJupiterVersion = providers.gradleProperty("junitJupiterVersion").get()
val assertjVersion = providers.gradleProperty("assertjVersion").get()
val javaToolchainVersion = providers.gradleProperty("javaToolchainVersion").get()
val gradleVersion = providers.gradleProperty("gradleVersion").get()
val kotlinPluginVersion = providers.gradleProperty("kotlinPluginVersion").get()
val platformSpiVersion = providers.gradleProperty("platformSpiBaselineVersion").get()

dependencies {
    api(project(":core:heron-platform-spi"))
    api(project(":core:heron-platform-runtime"))
    implementation(project(":bricks:heron-platform-registry"))
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly(project(":bricks:heron-platform-host-helidon"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// Expand the platform and SLF4J versions into the generated resource so that
// InitCommand never hard-codes them; the single source of truth is
// gradle.properties and the root project version.
tasks.processResources {
    filter(mapOf("tokens" to mapOf(
        "platformVersion" to project.version.toString(),
        "slf4jVersion" to slf4jVersion,
        "junitJupiterVersion" to junitJupiterVersion,
        "assertjVersion" to assertjVersion,
        "javaToolchainVersion" to javaToolchainVersion,
        "gradleVersion" to gradleVersion,
        "kotlinPluginVersion" to kotlinPluginVersion,
        "platformSpiVersion" to platformSpiVersion
    )), ReplaceTokens::class.java)
}
