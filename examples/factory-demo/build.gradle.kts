plugins {
    `java-library`
}

dependencies {
    implementation(project(":heron-platform-runtime"))
    implementation(project(":heron-platform-cli"))
    implementation(project(":examples:external-provider"))
}
