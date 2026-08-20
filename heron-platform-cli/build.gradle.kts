plugins {
    `java-library`
}

dependencies {
    api(project(":heron-platform-host-api"))
    api(project(":heron-platform-runtime"))
    implementation(project(":heron-platform-host-vertx"))
    testImplementation(project(":heron-platform-testkit"))
}
