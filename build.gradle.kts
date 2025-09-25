plugins {
    kotlin("jvm") version "2.0.20"
    id("com.gradleup.shadow") version "9.0.0-beta2"
    application
}

group = "com.github.albertocavalcante"
// x-release-please-start-version
val baseVersion = "0.1.0"
// x-release-please-end

// Smart version detection: SNAPSHOT for development, clean for releases
version = when {
    // GitHub releases (tagged)
    System.getenv("GITHUB_REF_TYPE") == "tag" -> baseVersion

    // Release-please PR builds
    System.getenv("GITHUB_HEAD_REF")?.contains("release-please") == true -> baseVersion

    // All other builds (development)
    else -> "$baseVersion-SNAPSHOT"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // LSP4J - Language Server Protocol implementation
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")

    // Groovy - For AST parsing and analysis
    implementation("org.apache.groovy:groovy:4.0.28")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "com.github.albertocavalcante.groovylsp.MainKt"
}

tasks.shadowJar {
    archiveBaseName = "groovy-lsp"
    archiveClassifier = ""
    manifest {
        attributes["Main-Class"] = "com.github.albertocavalcante.groovylsp.MainKt"
    }
}

// Fix task dependencies for Gradle 9
tasks.distZip {
    dependsOn(tasks.shadowJar)
}

tasks.distTar {
    dependsOn(tasks.shadowJar)
}

tasks.startScripts {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Debug task to print version information
tasks.register("printVersion") {
    doLast {
        println("Base version: $baseVersion")
        println("Final version: $version")
        println("GITHUB_REF_TYPE: ${System.getenv("GITHUB_REF_TYPE") ?: "not set"}")
        println("GITHUB_HEAD_REF: ${System.getenv("GITHUB_HEAD_REF") ?: "not set"}")
        println("Is release build: ${version == baseVersion}")
    }
}