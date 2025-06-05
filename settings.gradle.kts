plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "realtime-editor-backend"

include("core-ot")
include("backend-akka")
include("api-server")