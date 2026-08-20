plugins {
    `java-library`
}

dependencies {
    implementation(project(":heron-platform-spi"))
    testImplementation(project(":heron-platform-data"))
}

tasks.named<org.gradle.api.tasks.testing.Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}
