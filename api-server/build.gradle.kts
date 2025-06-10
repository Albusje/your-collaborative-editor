plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "3.1.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("application")
}

dependencies {
    implementation(project(":backend-akka"))
    implementation(project(":core-ot"))

    val ktorVersion = "3.1.3"

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    val akkaVersion = "2.10.5"
    val scalaBinVersion = "2.13"

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Core Akka dependencies for API server
    implementation("com.typesafe.akka:akka-actor_$scalaBinVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream_$scalaBinVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-slf4j_$scalaBinVersion:$akkaVersion")
    
    // Add Cassandra persistence dependencies since api-server uses backend actors
    implementation("com.typesafe.akka:akka-persistence_$scalaBinVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-persistence-cassandra_$scalaBinVersion:1.3.2")

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0")
    testImplementation("io.ktor:ktor-server-test-host")
}

application {
    mainClass.set("com.sasut.editor.api.ApplicationKt")
}

repositories {
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
    maven {
        url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    }
    mavenCentral()
    maven { url = uri("https://repo.akka.io/maven") }
}