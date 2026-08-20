plugins {
    `java-library`
}

dependencies {
    implementation(project(":heron-platform-runtime"))
    implementation(project(":heron-platform-cli"))
    implementation(project(":examples:external-provider"))
    testImplementation(project(":heron-platform-host-helidon"))
}

tasks.named<org.gradle.api.tasks.testing.Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}

// The standard shell must see this example's trusted ServiceLoader provider
// when its distribution is used with the factory-demo configuration.
project(":heron-platform-cli") {
    tasks.named<org.gradle.jvm.application.tasks.CreateStartScripts>("startScripts") {
        applicationName = "heron"
    }
    dependencies {
        runtimeOnly(project(":examples:external-provider"))
    }
}
