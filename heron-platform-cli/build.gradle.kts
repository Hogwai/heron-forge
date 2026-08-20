plugins {
    `java-library`
    application
}

application {
    mainClass.set("dev.hogwai.platform.cli.HeronLauncher")
}

dependencies {
    api(project(":heron-platform-host-api"))
    api(project(":heron-platform-runtime"))
    implementation(project(":heron-platform-host-helidon"))
    implementation("info.picocli:picocli:4.7.7")
}
