package com.sasut.editor.api.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.time.Duration

import akka.actor.ActorRef
import akka.pattern.Patterns.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration.Duration as FiniteDurationAkka
import scala.concurrent.ExecutionContextExecutor
import scala.util.Failure
import scala.util.Success
import com.sasut.editor.backend.actor.WebSocketBroadcastActor
import com.sasut.editor.api.actor.WebSocketClientHandlerActor
import com.sasut.editor.api.actor.SetWebSocketSession
import com.sasut.editor.backend.common.ActorSystemProvider
import com.sasut.editor.backend.command.ClientOperation
import com.sasut.editor.backend.command.DocumentActorRefResponse
import com.sasut.editor.backend.command.DocumentStateResponse
import com.sasut.editor.backend.command.GetDocumentActor
import com.sasut.editor.backend.command.GetDocumentState
import com.sasut.editor.backend.notification.DocumentUpdate
import com.sasut.editor.core.model.Delete
import com.sasut.editor.core.model.Insert
import com.sasut.editor.core.model.Operation
import com.sasut.editor.core.model.NoOp
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@kotlinx.serialization.Serializable
data class WebSocketOperation(
    val type: String,
    val position: Int? = null,
    val text: String? = null,
    val length: Int? = null,
    val clientVersion: Int,
    val clientId: String,
    val requestId: String = UUID.randomUUID().toString()
)

@kotlinx.serialization.Serializable
data class WebSocketDocumentUpdate(
    val type: String = "documentUpdate",
    val documentId: String,
    val transformedOperation: WebSocketOperation? = null,
    val newContent: String,
    val newVersion: Int
)


private val documentTimeout = Timeout(FiniteDurationAkka.create(15, TimeUnit.SECONDS))
private val log = LoggerFactory.getLogger("DocumentWebSocketRoutes")
private val json = Json { 
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val executionContext: ExecutionContextExecutor = ActorSystemProvider.system.dispatcher()

private fun <T> scalaFutureToCompletableFuture(scalaFuture: Future<T>): CompletableFuture<T> {
    val completableFuture = CompletableFuture<T>()
    scalaFuture.onComplete({ result ->
        when (result) {
            is Success -> completableFuture.complete(result.value())
            is Failure -> completableFuture.completeExceptionally(result.exception())
        }
    }, executionContext)
    return completableFuture
}

fun Route.documentWebSocketRouting() {
    webSocket("/ws/document/{documentId}") {
        val documentId = call.parameters["documentId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Document ID is required"))
        val sessionId = UUID.randomUUID().toString()

        log.info("WebSocket: Client connected to document {} with session ID {}", documentId, sessionId)

        val sessionActor = ActorSystemProvider.system.actorOf(
            com.sasut.editor.api.actor.WebSocketClientHandlerActor.props(documentId, ActorSystemProvider.webSocketBroadcaster),
            "ws-session-handler-$sessionId"
        )
        sessionActor.tell(com.sasut.editor.api.actor.SetWebSocketSession(this), ActorRef.noSender())

        var documentActor: ActorRef? = null
        try {
            val actorRefScalaFuture = ask(ActorSystemProvider.documentManager, GetDocumentActor(documentId, sessionId), documentTimeout)
            val response = scalaFutureToCompletableFuture(actorRefScalaFuture)
                .get(documentTimeout.duration().toMillis(), TimeUnit.MILLISECONDS)

            if (response is DocumentActorRefResponse) {
                documentActor = response.actorRef
                if (documentActor == null) {
                    log.error("WebSocket: Failed to get/create DocumentActor for {}. ActorRef is null.", documentId)
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Failed to get document actor"))
                    return@webSocket
                }
                log.debug("WebSocket: Got DocumentActorRef {} for document {}", documentActor.path(), documentId)
            } else {
                log.error("WebSocket: Unexpected response from DocumentManager for {}: {}", documentId, response)
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Unexpected response from document manager"))
                return@webSocket
            }

            val initialStateScalaFuture = ask(documentActor, GetDocumentState(sessionId), documentTimeout)
            val initialStateResponse = scalaFutureToCompletableFuture(initialStateScalaFuture)
                .get(documentTimeout.duration().toMillis(), TimeUnit.MILLISECONDS)

            if (initialStateResponse is DocumentStateResponse) {
                send(json.encodeToString(
                    WebSocketDocumentUpdate(
                        type = "documentUpdate",
                        documentId = documentId,
                        newContent = initialStateResponse.content,
                        newVersion = initialStateResponse.version
                    )
                ))
                log.info("WebSocket: Sent initial state for document {} (version {}) to session {}", documentId, initialStateResponse.version, sessionId)
            } else {
                log.error("WebSocket: Failed to get initial state for document {}: {}", documentId, initialStateResponse)
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Failed to get initial document state"))
                return@webSocket
            }

            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                val receivedText = frame.readText()
                log.debug("WebSocket: Received message from session {} for document {}: {}", sessionId, documentId, receivedText)

                try {
                    val clientOpDto = json.decodeFromString<WebSocketOperation>(receivedText)
                    val operation: Operation = when (clientOpDto.type) {
                        "insert" -> {
                            val pos = clientOpDto.position ?: throw IllegalArgumentException("Insert position missing")
                            val txt = clientOpDto.text ?: throw IllegalArgumentException("Insert text missing")
                            Insert(pos, txt)
                        }
                        "delete" -> {
                            val pos = clientOpDto.position ?: throw IllegalArgumentException("Delete position missing")
                            val len = clientOpDto.length ?: throw IllegalArgumentException("Delete length missing")
                            Delete(pos, len)
                        }
                        else -> throw IllegalArgumentException("Unknown operation type: ${clientOpDto.type}")
                    }

                    documentActor?.tell(
                        ClientOperation(
                            operation = operation,
                            clientId = clientOpDto.clientId,
                            clientVersion = clientOpDto.clientVersion,
                            requestId = clientOpDto.requestId
                        ),
                        ActorRef.noSender()
                    )
                } catch (e: Exception) {
                    log.error("WebSocket: Error processing incoming message from session {}: {}", sessionId, e.message, e)
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            log.info("WebSocket: Client session {} for document {} disconnected normally.", sessionId, documentId)
        } catch (e: Exception) {
            log.error("WebSocket: Error in session {} for document {}: {}", sessionId, documentId, e.message, e)
        } finally {
            ActorSystemProvider.system.stop(sessionActor)
            log.info("WebSocket: Client session {} for document {} handler actor stopped.", sessionId, documentId)
        }
    }
}