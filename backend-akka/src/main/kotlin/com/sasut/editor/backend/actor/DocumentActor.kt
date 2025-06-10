package com.sasut.editor.backend.actor

import java.util.LinkedList
import java.time.Duration

import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.persistence.AbstractPersistentActor
import akka.persistence.SnapshotOffer

import org.slf4j.LoggerFactory

import com.sasut.editor.backend.command.ClientOperation
import com.sasut.editor.backend.command.DocumentStateResponse
import com.sasut.editor.backend.command.GetDocumentState
import com.sasut.editor.backend.event.OperationAppliedEvent
import com.sasut.editor.backend.state.DocumentState
import com.sasut.editor.backend.notification.DocumentUpdate

import com.sasut.editor.core.model.NoOp
import com.sasut.editor.core.model.Operation
import com.sasut.editor.core.ot.OtEngine

class DocumentActor(
    private val documentId: String
) : AbstractPersistentActor() {

    private var state: DocumentState = DocumentState()
    private val recentOperations: LinkedList<OperationAppliedEvent> = LinkedList()
    private val MAX_RECENT_OPS = 100

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun props(documentId: String): Props {
            return Props.create(DocumentActor::class.java, documentId)
        }
    }

    override fun persistenceId(): String = "document-$documentId"

    override fun createReceiveRecover(): Receive {
        return receiveBuilder()
            .match(SnapshotOffer::class.java) { offer ->
                state = offer.snapshot() as DocumentState
                recentOperations.clear()
                log.info("DocumentActor {}: Recovered from snapshot at sequence number {}. Version: {}", documentId, offer.metadata().sequenceNr(), state.version)
            }
            .match(OperationAppliedEvent::class.java) { event ->
                try {
                    val newContent = event.transformedOperation.apply(state.content)
                    state = state.copy(
                        content = newContent,
                        version = event.newVersion
                    )
                } catch (e: IllegalArgumentException) {
                    log.error("DocumentActor {}: Failed to replay operation during recovery. Event: {}. Error: {}. Skipping event.", 
                        documentId, event, e.message)
                    // Skip this invalid event and continue recovery
                    state = state.copy(version = event.newVersion)
                }
            }
            .matchAny { message ->
                log.warn("DocumentActor {} received unexpected message during recovery: {}", documentId, message)
            }
            .build()
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClientOperation::class.java) { clientOp ->
                processClientOperation(clientOp, sender)
            }
            .match(GetDocumentState::class.java) { getDocState ->
                sender.tell(DocumentStateResponse(documentId, state.content, state.version), self)
                log.debug("DocumentActor {} sent state response to {}: Version {}", documentId, sender.path().name(), state.version)
            }
            .matchAny { message ->
                log.warn("DocumentActor {} received unknown message: {}", documentId, message)
            }
            .build()
    }

    private fun processClientOperation(clientOp: ClientOperation, originalSender: ActorRef) {
        log.debug("DocumentActor {} received operation from client {}: {}", documentId, clientOp.clientId, clientOp.operation)

        var transformedOp = clientOp.operation
        if (clientOp.clientVersion < state.version) {
            val opsToTransformAgainst = getOperationsToTransformAgainst(clientOp.clientVersion)
            for (serverOpEvent in opsToTransformAgainst) {
                transformedOp = OtEngine.transform(transformedOp, serverOpEvent.transformedOperation)
                if (transformedOp is NoOp) {
                    log.info("DocumentActor {}: Client operation became NoOp after transformation. Original: {}", documentId, clientOp.operation)
                    break
                }
            }
            log.debug("DocumentActor {}: Transformed operation from {} to {}. Original version: {}, Current version: {}",
                documentId, clientOp.operation, transformedOp, clientOp.clientVersion, state.version)
        } else if (clientOp.clientVersion > state.version) {
            log.warn("DocumentActor {}: Client {} sent operation with future version {}. Current version {}. Ignoring for now.",
                documentId, clientOp.clientId, clientOp.clientVersion, state.version)
            return
        }

        // Validate the operation before persisting
        try {
            // Test apply the operation to catch any validation errors
            transformedOp.apply(state.content)
        } catch (e: IllegalArgumentException) {
            log.error("DocumentActor {}: Invalid operation from client {}: {}. Error: {}", 
                documentId, clientOp.clientId, transformedOp, e.message)
            return
        }

        val newVersion = state.version + 1
        val event = OperationAppliedEvent(
            transformedOperation = transformedOp,
            newVersion = newVersion,
            originalClientId = clientOp.clientId,
            originalClientVersion = clientOp.clientVersion,
            originalRequestId = clientOp.requestId
        )

        persist(event) { persistedEvent ->
            log.debug("DocumentActor {}: Event persisted: {}", documentId, persistedEvent)

            state = state.copy(
                content = persistedEvent.transformedOperation.apply(state.content),
                version = persistedEvent.newVersion
            )
            log.info("DocumentActor {}: Document updated to version {}. Content length: {}", documentId, state.version, state.content.length)

            recentOperations.add(persistedEvent)
            if (recentOperations.size > MAX_RECENT_OPS) {
                recentOperations.removeFirst()
            }

            if (lastSequenceNr() % 100 == 0L) {
                saveSnapshot(state)
                log.info("DocumentActor {}: Saving snapshot at sequence number {}", documentId, lastSequenceNr())
            }

            context.system().eventStream().publish(
                DocumentUpdate(
                    documentId = documentId,
                    transformedOperation = persistedEvent.transformedOperation,
                    newVersion = state.version,
                    updatedContent = state.content
                )
            )
            log.debug("DocumentActor {}: Published DocumentUpdate for document version {}.", documentId, state.version)
        }
    }

    private fun getOperationsToTransformAgainst(clientVersion: Int): List<OperationAppliedEvent> {
        return recentOperations.filter { it.newVersion > clientVersion }
            .sortedBy { it.newVersion }
    }
}