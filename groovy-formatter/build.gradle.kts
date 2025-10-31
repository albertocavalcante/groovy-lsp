plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.openrewrite:rewrite-groovy:8.63.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.1")
}

tasks.test {
    useJUnitPlatform()
}
