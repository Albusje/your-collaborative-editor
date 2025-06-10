// backend-akka/build.gradle.kts

// Define versions consistently
val versions = mapOf(
    "AkkaVersion" to "2.10.5",
    "ScalaBinary" to "2.13",
    "AkkaPersistenceCassandraVersion" to "1.3.2",
)

dependencies {
    implementation(project(":core-ot"))

    val akkaVersion = "2.10.5" // Your desired core Akka version
    val scalaBinVersion = "2.13"
    val akkaPersistenceCassandraVersion = "1.3.2" // Update to 1.3.2

    // ESSENTIAL AKKA CORE CLASSIC ACTOR DEPENDENCIES
    implementation("com.typesafe.akka:akka-actor_${scalaBinVersion}:${akkaVersion}")
    implementation("com.typesafe.akka:akka-persistence_${scalaBinVersion}:${akkaVersion}")

    // AKKA PERSISTENCE CASSANDRA
    implementation("com.typesafe.akka:akka-persistence-cassandra_${scalaBinVersion}:${akkaPersistenceCassandraVersion}")

    implementation("com.typesafe.akka:akka-cluster_${scalaBinVersion}:${akkaVersion}")
    implementation("com.typesafe.akka:akka-cluster-tools_${scalaBinVersion}:${akkaVersion}")
    implementation("com.typesafe.akka:akka-coordination_${scalaBinVersion}:${akkaVersion}")
    implementation("com.typesafe.akka:akka-pki_${scalaBinVersion}:${akkaVersion}")
    implementation("com.typesafe.akka:akka-remote_${scalaBinVersion}:${akkaVersion}")
    implementation("com.typesafe.akka:akka-persistence-query_${scalaBinVersion}:${akkaVersion}")

    // Logging
    implementation("com.typesafe.akka:akka-slf4j_${scalaBinVersion}:${akkaVersion}")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // TESTING DEPENDENCIES
    testImplementation("com.typesafe.akka:akka-testkit_${scalaBinVersion}:${akkaVersion}")
    testImplementation("com.typesafe.akka:akka-persistence-testkit_${scalaBinVersion}:${akkaVersion}")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

// Keep repositories for robustness
repositories {
    mavenCentral()
    maven { url = uri("https://repo.akka.io/maven") }
}

tasks.test {
    useJUnitPlatform()
    include("**/com/sasut/editor/backend/actor/*Test.class")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}