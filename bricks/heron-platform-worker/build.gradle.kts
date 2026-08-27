plugins {
    `java-library`
    `maven-publish`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

dependencies {
    val helidonVersion = providers.gradleProperty("helidonVersion").get()
    val jacksonVersion = providers.gradleProperty("jacksonVersion").get()

    implementation(project(":core:heron-platform-spi"))
    implementation("io.helidon.webclient:helidon-webclient:$helidonVersion")
    implementation("io.helidon.http.media:helidon-http-media-jackson:$helidonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    annotationProcessor(project(":tools:heron-platform-processor"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
