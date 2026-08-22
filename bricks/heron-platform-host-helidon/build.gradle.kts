plugins {
    `java-library`
    `maven-publish`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

val helidonVersion = providers.gradleProperty("helidonVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()

dependencies {
    api(project(":core:heron-platform-spi"))
    annotationProcessor(project(":tools:heron-platform-processor"))
    implementation("io.helidon.webserver:helidon-webserver:$helidonVersion")
    implementation("io.helidon.http.media:helidon-http-media-jackson:$helidonVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
