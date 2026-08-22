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
    id("net.ltgt.errorprone") apply false
}

allprojects {
    group = "dev.hogwai.platform"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "pmd")
    apply(plugin = "checkstyle")
    apply(plugin = "net.ltgt.errorprone")

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
        add("errorprone", "com.google.errorprone:error_prone_core:$errorproneVersion")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }

    configure<CheckstyleExtension> {
        toolVersion = checkstyleVersion
        maxErrors = 0
        maxWarnings = 0
    }

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

    tasks.named<Javadoc>("javadoc") {
        options.encoding = "UTF-8"
    }

    // `check` must include tests, static analysis and coverage reporting.
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestReport"))
    }
}

tasks.register("createApp") {
    group = "heron"
    description = "Scaffold a new Heron application project"
    val projectName = providers.gradleProperty("name").orElse("my-app")
    val basePackage = providers.gradleProperty("package")
    doLast {
        val name = projectName.get()
        require(name.isNotBlank()) { "project name must not be blank" }
        require(!name.contains("/") && !name.contains("\\") && name != "." && name != "..") {
            "project name must be a simple directory name"
        }
        val pkg = if (basePackage.isPresent && basePackage.get().isNotBlank()) {
            basePackage.get()
        } else {
            name.lowercase(java.util.Locale.ROOT).replace("[^a-z0-9]".toRegex(), "").ifEmpty { "app" }
        }
        val target = rootProject.layout.projectDirectory.dir(name).asFile.toPath()
        require(!java.nio.file.Files.exists(target) || java.nio.file.Files.list(target).findAny().isEmpty) {
            "target directory '$name' already exists and is not empty"
        }
        java.nio.file.Files.createDirectories(target)
        val propsFile = rootProject.file("bricks/heron-platform-cli/src/main/resources/heron-platform.properties")
        val props = java.util.Properties()
        propsFile.inputStream().use { props.load(it) }
        val model = mapOf(
            "projectName" to name,
            "basePackage" to pkg,
            "platformVersion" to props.getProperty("platform.version"),
            "slf4jVersion" to props.getProperty("slf4j.version")
        )
        val templatesDir = rootProject.file("bricks/heron-platform-cli/src/main/resources/templates")
        for (templateName in listOf(
            "settings.gradle.kts.template",
            "build.gradle.kts.template",
            "application.yaml.template",
            "ProviderFactory.template",
            "HelloProviderFactory.java.template"
        )) {
            val rendered = templatesDir.resolve(templateName).readText(Charsets.UTF_8).let { template ->
                model.entries.fold(template) { acc, (k, v) -> acc.replace("{{$k}}", v) }
            }
            val relative = templateName.removeSuffix(".template")
            val out = when (templateName) {
                "HelloProviderFactory.java.template" ->
                    target.resolve("src/main/java/${pkg.replace('.', '/')}/$relative")
                "application.yaml.template" ->
                    target.resolve("src/main/resources/$relative")
                "ProviderFactory.template" ->
                    target.resolve("src/main/resources/META-INF/services/dev.hogwai.platform.spi.provider.ProviderFactory")
                else -> target.resolve(relative)
            }
            java.nio.file.Files.createDirectories(out.parent)
            java.nio.file.Files.writeString(out, rendered, Charsets.UTF_8)
        }
        println("Created Heron project '$name'")
        println("Next steps:")
        println("  cd $name")
        println("  ./gradlew installDist")
        println("  ./build/install/$name/bin/$name start --config src/main/resources/application.yaml")
    }
}
