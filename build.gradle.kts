plugins {
    // Apply the Java plugin to the root project (can be useful for common tasks)
    java

    // Declare the Kotlin JVM plugin version here.
    // 'apply false' prevents it from being applied to the root project itself,
    // but makes its capabilities available for configuration in subprojects.
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

allprojects {
    group = "com.sasut.editor" // Common group ID for all modules
    version = "1.0-SNAPSHOT" // Common version for all modules

    repositories {
        mavenCentral()
        // Add other repositories if needed, e.g., for specific Akka SNAPSHOTs or commercial versions
        maven { url = uri("https://repo.akka.io/maven") } // Ensure Akka repo is here for all projects
    }
}

subprojects {
    // This correctly applies the Kotlin JVM plugin to each subproject
    apply(plugin = "org.jetbrains.kotlin.jvm")

    // Configure Kotlin compilation options for each subproject
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17" // Ensure consistent JVM target
            // Add other Kotlin compiler options if needed
            freeCompilerArgs = listOf("-Xjvm-default=all") // Recommended for Akka with Kotlin
        }
    }
}