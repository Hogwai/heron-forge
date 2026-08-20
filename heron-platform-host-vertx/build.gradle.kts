plugins {
    `java-library`
}

dependencies {
    api(project(":heron-platform-host-api"))
    implementation("io.vertx:vertx-core:${property("vertxVersion")}")
    implementation("io.vertx:vertx-web:${property("vertxVersion")}")
    testImplementation("io.vertx:vertx-junit5:${property("vertxVersion")}")
}
