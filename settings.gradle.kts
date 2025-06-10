// Keep the toolchains plugin, but move it INTO the pluginManagement block.
// This ensures ALL plugin resolution (including Ktor) is configured together.
pluginManagement {
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://repo.akka.io/maven") }
        maven {
            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        }
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        }
        // --- END IMPORTANT ---
    }
}
rootProject.name = "realtime-editor-backend"

include("core-ot")
include("backend-akka")
include("api-server")