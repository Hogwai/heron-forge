plugins {
    `java-library`
    application
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
    runtimeOnly("org.slf4j:slf4j-simple:${providers.gradleProperty("slf4jVersion").get()}")
}

tasks.named<Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}
