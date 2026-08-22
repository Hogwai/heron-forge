import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
    `maven-publish`
    application
}

application {
    mainClass.set("dev.hogwai.platform.cli.HeronLauncher")
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()
val picocliVersion = providers.gradleProperty("picocliVersion").get()

dependencies {
    api(project(":core:heron-platform-spi"))
    api(project(":core:heron-platform-runtime"))
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
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
        "slf4jVersion" to slf4jVersion
    )), ReplaceTokens::class.java)
}
