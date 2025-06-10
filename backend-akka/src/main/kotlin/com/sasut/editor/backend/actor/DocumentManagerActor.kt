package com.sasut.editor.backend.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import com.sasut.editor.backend.command.DocumentActorRefResponse
import com.sasut.editor.backend.command.GetDocumentActor
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

// Commands that the DocumentManagerActor understands
// These are defined in DocumentCommands.kt, but re-iterated here for clarity
// data class GetDocumentActor(val documentId: String, val requestId: String = "")
// data class DocumentActorRefResponse(val documentId: String, val actorRef: ActorRef?, val requestId: String = "")

class DocumentManagerActor : AbstractActor() {
    private val log = LoggerFactory.getLogger(javaClass)
    // Map to store ActorRefs of DocumentActor instances, keyed by documentId
    private val documentActors: MutableMap<String, ActorRef> = ConcurrentHashMap()

    companion object {
        // Factory method to create Props for this actor
        fun props(): Props {
            return Props.create(DocumentManagerActor::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            // Handle requests to get or create a DocumentActor for a specific ID
            .match(GetDocumentActor::class.java) { msg ->
                val documentId = msg.documentId
                val existingActor = documentActors[documentId]

                if (existingActor != null) {
                    log.debug("DocumentManager: Found existing actor {} for document {}", existingActor.path(), documentId)
                    // If actor already exists, send its reference back to the sender
                    sender.tell(DocumentActorRefResponse(documentId, existingActor, msg.requestId), self)
                } else {
                    // If no actor exists for this documentId, create a new one
                    // Use ActorSystem's context to create child actors
                    val newActor = context.actorOf(DocumentActor.props(documentId), "document-$documentId")

                    // IMPORTANT: Watch the new actor. If it dies, we want to know so we can remove it from our map.
                    context.watch(newActor)

                    documentActors[documentId] = newActor // Store the new actor's reference
                    log.info("DocumentManager: Created new actor {} for document {}", newActor.path(), documentId)
                    sender.tell(DocumentActorRefResponse(documentId, newActor, msg.requestId), self)
                }
            }
            // Handle Terminated messages from watched actors
            .match(Terminated::class.java) { terminated ->
                val terminatedActorRef = terminated.getActor()
                // Find the documentId associated with the terminated actor
                val terminatedDocumentId = documentActors.filterValues { it == terminatedActorRef }.keys.firstOrNull()

                if (terminatedDocumentId != null) {
                    documentActors.remove(terminatedDocumentId)
                    log.warn("DocumentManager: Document actor {} for document {} terminated. Removed from registry.", terminatedActorRef.path(), terminatedDocumentId)
                    // In a a more robust production system, you might:
                    // 1. Log extensively
                    // 2. Potentially re-spawn the actor (e.g., using a supervisor strategy or a separate actor management pattern)
                    // 3. Notify connected clients that the document is temporarily unavailable.
                } else {
                    log.warn("DocumentManager: Unknown actor {} (not in our registry) terminated.", terminatedActorRef.path())
                }
            }
            // Handle any other unexpected messages
            .matchAny { message ->
                log.warn("DocumentManager: Received unknown message: {}", message)
            }
            .build()
    }
}