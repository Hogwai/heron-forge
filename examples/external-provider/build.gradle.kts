plugins {
    `java-library`
}

dependencies {
    implementation(project(":heron-platform-spi"))
    testImplementation(project(":heron-platform-testkit"))
}
