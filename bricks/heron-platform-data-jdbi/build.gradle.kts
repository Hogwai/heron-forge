plugins {
    `java-library`
    `maven-publish`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

val jdbiVersion = providers.gradleProperty("jdbiVersion").get()
val hikariVersion = providers.gradleProperty("hikariVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()

dependencies {
    implementation(project(":core:heron-platform-spi"))
    implementation(platform("org.jdbi:jdbi3-bom:$jdbiVersion"))
    implementation("org.jdbi:jdbi3-core")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
