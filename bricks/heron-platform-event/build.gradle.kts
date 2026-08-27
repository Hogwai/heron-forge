plugins {
    `java-library`
    `maven-publish`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

dependencies {
    implementation(project(":core:heron-platform-spi"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
