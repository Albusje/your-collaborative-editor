package com.sasut.editor.api

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import com.sasut.editor.backend.common.ActorSystemProvider
import com.sasut.editor.api.routes.documentWebSocketRouting
import java.time.Duration
import kotlin.time.toKotlinDuration
import io.ktor.server.response.respondText


fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureWebSockets()
        configureRouting()
    }.start(wait = true)

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down ActorSystem...")
        ActorSystemProvider.shutdown()
        println("ActorSystem shut down.")
    })
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureWebSockets() {
    install(WebSockets) {
        // --- CONVERT JAVA DURATION TO KOTLIN DURATION ---
        pingPeriod = Duration.ofSeconds(15).toKotlinDuration()
        timeout = Duration.ofSeconds(15).toKotlinDuration()
        // --- END CONVERSION ---
        maxFrameSize = 128_000
        masking = false
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Collaborative Editor Backend is running!")
        }
        documentWebSocketRouting()
    }
}