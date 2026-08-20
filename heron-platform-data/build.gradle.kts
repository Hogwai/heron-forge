plugins {
    `java-library`
}

val jdbiVersion = providers.gradleProperty("jdbiVersion").get()
val postgresqlVersion = providers.gradleProperty("postgresqlVersion").get()

dependencies {
    implementation(project(":heron-platform-spi"))
    implementation(platform("org.jdbi:jdbi3-bom:$jdbiVersion"))
    implementation("org.jdbi:jdbi3-core")
    implementation("org.jdbi:jdbi3-postgres")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
}

tasks.named<org.gradle.api.tasks.testing.Test>("test") {
    inputs.property("RUN_POSTGRES_TESTS", providers.environmentVariable("RUN_POSTGRES_TESTS").orElse(""))
}
