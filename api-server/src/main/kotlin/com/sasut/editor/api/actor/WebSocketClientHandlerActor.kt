package com.sasut.editor.api.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import com.sasut.editor.backend.notification.DocumentUpdate
import com.sasut.editor.api.routes.WebSocketDocumentUpdate // Import the DTO
import java.io.IOException

// Command to tell this actor about its Ktor WebSocket session
data class SetWebSocketSession(val session: DefaultWebSocketSession)

class WebSocketClientHandlerActor(
    private val documentId: String,
    private val broadcastActor: ActorRef
) : AbstractActor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private var webSocketSession: DefaultWebSocketSession? = null
    
    // Configure JSON to include default values
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        fun props(documentId: String, broadcastActor: ActorRef): Props {
            return Props.create(WebSocketClientHandlerActor::class.java, documentId, broadcastActor)
        }
    }

    override fun preStart() {
        log.debug("WebSocketClientHandlerActor for document {} created.", documentId)
    }

    override fun postStop() {
        // Unregister from broadcastActor when this actor stops
        broadcastActor.tell(com.sasut.editor.backend.actor.UnregisterWebSocketSession(documentId, self), self)
        log.debug("WebSocketClientHandlerActor for document {} stopped and unregistered.", documentId)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            // Store the Ktor WebSocket session reference
            .match(SetWebSocketSession::class.java) { msg ->
                this.webSocketSession = msg.session
                // Once session is set, register with the broadcast actor
                broadcastActor.tell(com.sasut.editor.backend.actor.RegisterWebSocketSession(documentId, self), self)
                log.debug("WebSocketClientHandlerActor for document {}: Ktor session set and registered with broadcaster.", documentId)
            }
            // Receive DocumentUpdate messages from WebSocketBroadcastActor
            .match(DocumentUpdate::class.java) { update ->
                log.debug("WebSocketClientHandlerActor for document {}: Received DocumentUpdate version {}. Sending to client.", update.documentId, update.newVersion)
                webSocketSession?.let { session ->
                    try {
                        val webSocketUpdate = WebSocketDocumentUpdate(
                            type = "documentUpdate", // Explicitly set the type
                            documentId = update.documentId,
                            transformedOperation = null, // Or transform back for client if needed
                            newContent = update.updatedContent,
                            newVersion = update.newVersion
                        )
                        session.outgoing.trySend(Frame.Text(json.encodeToString(webSocketUpdate))).getOrThrow()
                    } catch (e: IOException) {
                        log.error("WebSocketClientHandlerActor: Error sending update to session for document {}: {}", documentId, e.message)
                        // Handle potential closed session here (e.g., stop self)
                    } catch (e: Exception) {
                        log.error("WebSocketClientHandlerActor: Serialization or other error sending update for document {}: {}", documentId, e.message, e)
                    }
                } ?: log.warn("WebSocketClientHandlerActor for document {}: Received update but no session set.", documentId)
            }
            // No other messages are expected to be handled by this actor
            .matchAny { message ->
                log.warn("WebSocketClientHandlerActor for document {}: Received unexpected message: {}", documentId, message)
            }
            .build()
    }
}