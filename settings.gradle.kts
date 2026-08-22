pluginManagement {
    val jmhPluginVersion = providers.gradleProperty("jmhPluginVersion").get()
    val errorpronePluginVersion = providers.gradleProperty("errorpronePluginVersion").get()
    val revapiPluginVersion = providers.gradleProperty("revapiPluginVersion").get()
    val kotlinPluginVersion = providers.gradleProperty("kotlinPluginVersion").get()

    plugins {
        id("me.champeau.jmh") version jmhPluginVersion
        id("net.ltgt.errorprone") version errorpronePluginVersion
        id("org.revapi.revapi-gradle-plugin") version revapiPluginVersion
        id("org.jetbrains.kotlin.jvm") version kotlinPluginVersion
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

include("core:heron-platform-spi")
include("core:heron-platform-runtime")
include("bricks:heron-platform-data-postgresql")
include("bricks:heron-platform-host-helidon")
include("bricks:heron-platform-cli")
include("examples:external-provider")
include("examples:kotlin-provider")
include("examples:factory-demo")
