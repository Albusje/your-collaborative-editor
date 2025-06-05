plugins {
    // This module only needs the Kotlin JVM plugin
    kotlin("jvm")
}

dependencies {
    // Standard Kotlin library
    implementation(kotlin("stdlib-jdk8"))

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}