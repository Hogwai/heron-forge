plugins {
    `java-library`
}

val helidonVersion = providers.gradleProperty("helidonVersion").get()

dependencies {
    api(project(":heron-platform-host-api"))
    implementation("io.helidon.webserver:helidon-webserver:$helidonVersion")
    implementation("io.helidon.http.media:helidon-http-media-jackson:$helidonVersion")
}
