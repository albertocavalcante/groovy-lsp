pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}

rootProject.name = "jenkins-pipeline-metadata-extractor"

// Include dependencies extracted into the 'deps' directory
include("deps:groovy-common")
include("deps:groovy-gdsl")
include("deps:groovy-build-tool")
include("deps:groovy-jenkins")

// Explicitly set project directories for the dependencies
project(":deps:groovy-common").projectDir = file("deps/groovy-common")
project(":deps:groovy-gdsl").projectDir = file("deps/groovy-gdsl")
project(":deps:groovy-build-tool").projectDir = file("deps/groovy-build-tool")
project(":deps:groovy-jenkins").projectDir = file("deps/groovy-jenkins")
