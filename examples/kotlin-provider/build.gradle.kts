plugins {
    `java-library`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
}

val javaToolchainVersion = providers.gradleProperty("javaToolchainVersion").get().toInt()

kotlin {
    jvmToolchain(javaToolchainVersion)
}

dependencies {
    implementation(project(":core:heron-platform-spi"))
    kapt(project(":tools:heron-platform-processor"))
}
