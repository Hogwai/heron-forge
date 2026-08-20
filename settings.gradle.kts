pluginManagement {
    val jmhPluginVersion = providers.gradleProperty("jmhPluginVersion").get()
    val errorpronePluginVersion = providers.gradleProperty("errorpronePluginVersion").get()

    plugins {
        id("me.champeau.jmh") version jmhPluginVersion
        id("net.ltgt.errorprone") version errorpronePluginVersion
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Supports the local POC baseline.
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "heron-forge"

include("heron-platform-spi")
include("heron-platform-runtime")
include("heron-platform-host-api")
include("heron-platform-host-vertx")
include("heron-platform-cli")
include("heron-platform-testkit")
include("heron-platform-otel")
include("examples:external-provider")
include("examples:factory-demo")
