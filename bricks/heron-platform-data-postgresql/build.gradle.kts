plugins {
    `java-library`
    `maven-publish`
    id("jacoco")
    id("pmd")
    id("checkstyle")
    id("net.ltgt.errorprone")
}

val jdbiVersion = providers.gradleProperty("jdbiVersion").get()
val postgresqlVersion = providers.gradleProperty("postgresqlVersion").get()
val archunitVersion = providers.gradleProperty("archunitVersion").get()

dependencies {
    api(project(":bricks:heron-platform-data-jdbi"))
    annotationProcessor(project(":tools:heron-platform-processor"))
    implementation(project(":core:heron-platform-spi"))
    implementation(platform("org.jdbi:jdbi3-bom:$jdbiVersion"))
    implementation("org.jdbi:jdbi3-postgres")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.named<Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}
