plugins {
    `java-library`
}

val otelVersion = providers.gradleProperty("otelVersion").get()

dependencies {
    api(project(":heron-platform-spi"))
    api("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
}
