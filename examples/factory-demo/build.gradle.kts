plugins {
    `java-library`
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

dependencies {
    implementation(project(":core:heron-platform-runtime"))
    implementation(project(":bricks:heron-platform-cli"))
    implementation(project(":bricks:heron-platform-data-postgresql"))
    implementation(project(":bricks:heron-platform-host-helidon"))
    implementation(project(":examples:external-provider"))
    implementation(project(":examples:kotlin-provider"))
    testImplementation(project(":bricks:heron-platform-registry"))
    runtimeOnly("org.slf4j:slf4j-simple:${providers.gradleProperty("slf4jVersion").get()}")
}

tasks.named<Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}
