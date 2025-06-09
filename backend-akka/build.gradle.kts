// backend-akka/build.gradle.kts

// Define versions consistently
val versions = mapOf(
    "AkkaVersion" to "2.10.5", // Keep Akka core at 2.10.5
    "ScalaBinary" to "2.13",
    // AkkaPersistenceJdbcVersion was removed
    "AkkaPersistenceCassandraVersion" to "1.3.1" // <<< CHANGE THIS VERSION TO 1.3.1
)

dependencies {
    // Project dependencies
    implementation(project(":core-ot"))

    // ESSENTIAL AKKA CORE CLASSIC ACTOR DEPENDENCIES
    implementation("com.typesafe.akka:akka-actor_${versions["ScalaBinary"]}:${versions["AkkaVersion"]}")
    implementation("com.typesafe.akka:akka-persistence_${versions["ScalaBinary"]}:${versions["AkkaVersion"]}")

    // --- SWITCH TO AKKA PERSISTENCE CASSANDRA ---
    // Update to version 1.3.1 which should be compatible with Akka 2.10.x
    implementation("com.typesafe.akka:akka-persistence-cassandra_${versions["ScalaBinary"]}:${versions["AkkaPersistenceCassandraVersion"]}")

    // Cassandra driver is often transitive, but can be added explicitly if needed.
    // You'll need a running Cassandra instance for this.

    // Remove PostgreSQL driver if not using other PostgreSQL features
    // implementation("org.postgresql:postgresql:42.7.3")


    // TESTING DEPENDENCIES
    testImplementation("com.typesafe.akka:akka-testkit_${versions["ScalaBinary"]}:${versions["AkkaVersion"]}")
    // Use standard akka-persistence-testkit for in-memory testing
    testImplementation("com.typesafe.akka:akka-persistence-testkit_${versions["ScalaBinary"]}:${versions["AkkaVersion"]}")

    // Kotlin testing and JUnit 5
    testImplementation(kotlin("test"))
    // === IMPORTANT: Change to testImplementation for the engine ===
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0") // Ensure API is also testImplementation
    // === End of change ===
    // LOGGING
    // Add the Akka SLF4J logging adapter
    implementation("com.typesafe.akka:akka-slf4j_${versions["ScalaBinary"]}:${versions["AkkaVersion"]}") // Use versions map here
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

// Keep repositories for robustness
repositories {
    mavenCentral()
    maven { url = uri("https://repo.akka.io/maven") }
}

tasks.test {
    useJUnitPlatform() // Ensure this is present for JUnit 5

    // Add these lines to explicitly include/exclude test patterns if needed
    include("**/com/sasut/editor/backend/actor/*Test.class") // Include test classes ending with Test

    // Set test logging level if you want more verbose output during test run
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true // Show System.out/err in test logs
    }
}

