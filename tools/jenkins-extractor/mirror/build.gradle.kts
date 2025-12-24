plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    // Project Dependencies (mapped to local deps/ folder)
    implementation(project(":deps:groovy-jenkins"))
    implementation(project(":deps:groovy-gdsl"))

    // Libraries
    implementation(libs.kotlin.serialization.json)
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.github.albertocavalcante.groovyjenkins.extractor.GdslToJsonKt")
}

tasks.test {
    useJUnitPlatform()
}

// Simple versioning for the standalone tool
version = "0.1.0-SNAPSHOT"
group = "com.github.albertocavalcante.jenkins.extractor"

// Ensure we use the same Java toolchain as the main repo (Java 21)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
