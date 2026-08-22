plugins {
    `java-library`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

dependencies {
    implementation(project(":core:heron-platform-spi"))
    testImplementation(project(":bricks:heron-platform-data-postgresql"))
}

tasks.named<Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}
