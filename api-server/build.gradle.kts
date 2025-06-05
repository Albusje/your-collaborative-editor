plugins {
    // This module only needs the Kotlin JVM plugin
    kotlin("jvm")
    // If using Ktor, add Ktor plugin here:
    // id("io.ktor.jvm") version "2.3.9" // Check for latest Ktor version
}

dependencies {
    // Depends on the backend-akka module
    implementation(project(":backend-akka"))

    // Ktor dependencies (if chosen)
    val ktorVersion = "2.3.9" // Check for the latest Ktor version
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion") // Or other engine like CIO
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion") // For JSON/other content types
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion") // For kotlinx.serialization

    // Logging (optional but recommended)
    implementation("ch.qos.logback:logback-classic:1.5.6") // Check for latest version

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

// Ktor-specific task if you added the Ktor plugin
// application {
//     mainClass.set("com.yourcompany.editor.api.ApplicationKt") // Or your main class name
// }