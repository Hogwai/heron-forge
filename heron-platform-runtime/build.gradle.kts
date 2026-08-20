plugins {
    `java-library`
    id("me.champeau.jmh")
}

val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val jacksonVersion = providers.gradleProperty("jacksonVersion").get()
val jmhVer = providers.gradleProperty("jmhVersion").get()

dependencies {
    api(project(":heron-platform-spi"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
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
