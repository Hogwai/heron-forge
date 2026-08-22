plugins {
    `java-library`
    `maven-publish`
    id("me.champeau.jmh")
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val jacksonVersion = providers.gradleProperty("jacksonVersion").get()
val snakeyamlEngineVersion = providers.gradleProperty("snakeyamlEngineVersion").get()
val jmhVer = providers.gradleProperty("jmhVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()

dependencies {
    api(project(":core:heron-platform-spi"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.snakeyaml:snakeyaml-engine:$snakeyamlEngineVersion")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

jmh {
    jmhVersion = jmhVer
}

// Coverage gate: at least 80% instruction coverage for the runtime module.
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
