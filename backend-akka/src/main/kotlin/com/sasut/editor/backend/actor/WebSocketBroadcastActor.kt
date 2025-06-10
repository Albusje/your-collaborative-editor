package com.sasut.editor.backend.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import org.slf4j.LoggerFactory
import com.sasut.editor.backend.notification.DocumentUpdate
import java.util.concurrent.ConcurrentHashMap

// Commands for WebSocketBroadcastActor
data class RegisterWebSocketSession(val documentId: String, val sessionActor: ActorRef)
data class UnregisterWebSocketSession(val documentId: String, val sessionActor: ActorRef)

class WebSocketBroadcastActor : AbstractActor() {
    private val log = LoggerFactory.getLogger(javaClass)

    // Map: documentId -> Set of ActorRef (representing individual WebSocket sessions)
    private val documentSubscribers: ConcurrentHashMap<String, MutableSet<ActorRef>> = ConcurrentHashMap()

    companion object {
        fun props(): Props {
            return Props.create(WebSocketBroadcastActor::class.java)
        }
    }

    override fun preStart() {
        // Subscribe to DocumentUpdate events when the actor starts
        context.system().eventStream().subscribe(self, DocumentUpdate::class.java)
        log.info("WebSocketBroadcastActor subscribed to Akka EventStream for DocumentUpdate messages.")
    }

    override fun postStop() {
        // Unsubscribe when the actor stops
        context.system().eventStream().unsubscribe(self)
        log.info("WebSocketBroadcastActor unsubscribed from Akka EventStream.")
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            // Handle registration of a new WebSocket session
            .match(RegisterWebSocketSession::class.java) { msg ->
                documentSubscribers.computeIfAbsent(msg.documentId) { ConcurrentHashMap.newKeySet() }.add(msg.sessionActor)
                // Watch the sessionActor so we know if the WebSocket client disconnects
                context.watch(msg.sessionActor)
                log.info("WebSocketBroadcastActor: Session {} registered for document {}. Total subscribers: {}", msg.sessionActor.path(), msg.documentId, documentSubscribers[msg.documentId]?.size)
            }
            // Handle unregistration of a WebSocket session
            .match(UnregisterWebSocketSession::class.java) { msg ->
                val removed = documentSubscribers[msg.documentId]?.remove(msg.sessionActor)
                if (removed == true) {
                    log.info("WebSocketBroadcastActor: Session {} unregistered for document {}. Total subscribers: {}", msg.sessionActor.path(), msg.documentId, documentSubscribers[msg.documentId]?.size)
                }
                // Stop watching if the session explicitly unregistered
                context.unwatch(msg.sessionActor)
            }
            // Handle termination of a watched WebSocket session actor
            .match(Terminated::class.java) { terminated ->
                val terminatedActorRef = terminated.getActor()
                // Find and remove the terminated actor from all document subscriptions
                var documentIdRemovedFrom: String? = null
                documentSubscribers.forEach { (docId, sessions) ->
                    if (sessions.remove(terminatedActorRef)) {
                        documentIdRemovedFrom = docId
                        log.info("WebSocketBroadcastActor: Watched session actor {} for document {} terminated. Removed from registry. Remaining: {}", terminatedActorRef.path(), docId, sessions.size)
                        // If no more subscribers for a document, optionally clean up the empty set
                        if (sessions.isEmpty()) {
                            documentSubscribers.remove(docId)
                            log.info("WebSocketBroadcastActor: No more subscribers for document {}. Removed empty set.", docId)
                        }
                        return@forEach // Exit forEach after finding
                    }
                }
                if (documentIdRemovedFrom == null) {
                    log.warn("WebSocketBroadcastActor: Watched unknown actor {} terminated (not found in active subscriptions).", terminatedActorRef.path())
                }
            }
            // Handle DocumentUpdate messages from EventStream
            .match(DocumentUpdate::class.java) { update ->
                log.debug("WebSocketBroadcastActor: Received DocumentUpdate for document {}: Version {}. Fanning out.", update.documentId, update.newVersion)
                val sessionsForDocument = documentSubscribers[update.documentId]
                sessionsForDocument?.forEach { sessionActor ->
                    // Forward the DocumentUpdate message to each session's actor
                    sessionActor.tell(update, self)
                }
                if (sessionsForDocument.isNullOrEmpty()) {
                    log.debug("WebSocketBroadcastActor: No active sessions for document {}. Update not fanned out.", update.documentId)
                }
            }
            // Handle any other unknown messages
            .matchAny { message ->
                log.warn("WebSocketBroadcastActor: Received unknown message: {}", message)
            }
            .build()
    }
}