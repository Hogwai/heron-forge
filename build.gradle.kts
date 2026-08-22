// Revapi Gradle integration: the `org.revapi.revapi-gradle-plugin` embeds
// `org.revapi:revapi-java:0.19.1` on its own classpath and exposes no
// extension to select the analyzer version. We therefore add the analyzer to
// the buildscript classpath and force the version declared in gradle.properties
// so the effective analyzer is `org.revapi:revapi-java:$revapiVersion`.
buildscript {
    val revapiVersion = providers.gradleProperty("revapiVersion").get()
    val revapiPluginVersion = providers.gradleProperty("revapiPluginVersion").get()

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.revapi:gradle-revapi:$revapiPluginVersion")
        classpath("org.revapi:revapi-java:$revapiVersion")
    }

    configurations.classpath {
        resolutionStrategy {
            force("org.revapi:revapi-java:$revapiVersion")
        }
    }
}

plugins {
    base
}

allprojects {
    group = "dev.hogwai.platform"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    pluginManager.withPlugin("java-library") {
        val javaToolchainVersion = providers.gradleProperty("javaToolchainVersion").get()
        val junitJupiterVersion = providers.gradleProperty("junitJupiterVersion").get()
        val assertjVersion = providers.gradleProperty("assertjVersion").get()
        val errorproneVersion = providers.gradleProperty("errorproneVersion").get()
        val pmdVersion = providers.gradleProperty("pmdVersion").get()
        val checkstyleVersion = providers.gradleProperty("checkstyleVersion").get()
        val jacocoVersion = providers.gradleProperty("jacocoVersion").get()

        configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(javaToolchainVersion)
            }
        }

        dependencies {
            add("testImplementation", platform("org.junit:junit-bom:$junitJupiterVersion"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
            add("testImplementation", "org.assertj:assertj-core:$assertjVersion")
        }

        pluginManager.withPlugin("net.ltgt.errorprone") {
            dependencies {
                add("errorprone", "com.google.errorprone:error_prone_core:$errorproneVersion")
            }
        }

        tasks.named<Test>("test") {
            useJUnitPlatform()
        }

        pluginManager.withPlugin("checkstyle") {
            configure<CheckstyleExtension> {
                toolVersion = checkstyleVersion
                maxErrors = 0
                maxWarnings = 0
            }
        }

        pluginManager.withPlugin("pmd") {
            configure<PmdExtension> {
                toolVersion = pmdVersion
                ruleSetFiles = files("${rootDir}/config/pmd/pmd-rules.xml")
                ruleSets = listOf()
                isConsoleOutput = true
            }

            // Test sources use a dedicated ruleset that excludes CyclomaticComplexity,
            // which adds noise on tests without guarding production quality.
            tasks.named<Pmd>("pmdTest") {
                ruleSetFiles = files("${rootDir}/config/pmd/pmd-rules-test.xml")
            }
        }

        pluginManager.withPlugin("jacoco") {
            configure<JacocoPluginExtension> {
                toolVersion = jacocoVersion
            }

            tasks.named<JacocoReport>("jacocoTestReport") {
                dependsOn(tasks.named("test"))
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }

            // `check` must include tests, static analysis and coverage reporting.
            tasks.named("check") {
                dependsOn(tasks.named("jacocoTestReport"))
            }
        }

        tasks.named<Javadoc>("javadoc") {
            options.encoding = "UTF-8"
        }
    }
}
