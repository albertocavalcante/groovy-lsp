plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Groovy
    implementation(libs.groovy.core)

    // Kotlin
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.core)

    // Logging
    implementation(libs.slf4j.api)

    // ClassGraph for scanning plugin JARs
    implementation(libs.classgraph)

    // HTML to Markdown conversion for vars documentation
    implementation(libs.flexmark.html2md)

    api(project(":groovy-gdsl"))
    api(project(":groovy-build-tool"))

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // PoC: Real Jenkins JARs for scanning verification (using Central versions)
    // testImplementation("org.jenkins-ci.main:jenkins-core:2.426.1") {
    //     exclude(group = "javax.servlet", module = "servlet-api")
    // }
    // testImplementation("org.jenkins-ci.plugins:workflow-step-api:2.24")
    // testImplementation("org.jenkins-ci.plugins:workflow-basic-steps:2.24")
    // testImplementation("org.jenkins-ci.plugins:workflow-durable-task-step:2.40")
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Detekt formatting
    detektPlugins(libs.detekt.formatting)
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
