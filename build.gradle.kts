plugins {
    java

    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

allprojects {
    group = "com.sasut.editor"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.akka.io/maven") }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }
}