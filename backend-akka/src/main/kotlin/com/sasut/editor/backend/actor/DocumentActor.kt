package com.sasut.editor.backend.actor

// --- Standard Java/Kotlin Imports ---
import java.util.LinkedList
import java.time.Duration // Only if you uncomment code using Duration, like expectMsg in tests or a timeout

// --- Akka Core Imports ---
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.ActorSystem // Needed if you use ActorSystem.create inside the actor, typically done outside
import akka.persistence.AbstractPersistentActor
import akka.persistence.SnapshotOffer // Needed for handling SnapshotOffer in recovery

// --- SLF4J Logging ---
import org.slf4j.LoggerFactory

// --- Project-Specific Imports ---
// From backend-akka/command module
import com.sasut.editor.backend.command.ClientOperation
import com.sasut.editor.backend.command.DocumentStateResponse
import com.sasut.editor.backend.command.GetDocumentState
import com.sasut.editor.backend.event.OperationAppliedEvent
import com.sasut.editor.backend.state.DocumentState
import com.sasut.editor.backend.notification.DocumentUpdate

// From core-ot module
import com.sasut.editor.core.model.NoOp
import com.sasut.editor.core.model.Operation
import com.sasut.editor.core.ot.OtEngine

class DocumentActor(
    private val documentId: String
) : AbstractPersistentActor() {

    private var state: DocumentState = DocumentState()
    private val recentOperations: LinkedList<OperationAppliedEvent> = LinkedList() // For OT transformation history
    private val MAX_RECENT_OPS = 100 // Keep a window of recent ops for transformation

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun props(documentId: String): Props {
            return Props.create(DocumentActor::class.java, documentId)
        }
    }

    // Define the persistenceId for this actor instance.
    // This string uniquely identifies the actor's event stream in the journal.
    override fun persistenceId(): String = "document-$documentId"

    // --- Recovery Logic ---
    // This method defines how the actor recovers its state from persisted events and snapshots
    // when it starts up or restarts. Akka replays events from the journal.
    override fun createReceiveRecover(): Receive {
        return receiveBuilder()
            .match(SnapshotOffer::class.java) { offer ->
                // If a snapshot is available, recover state from it.
                // This skips replaying all events before the snapshot, speeding up recovery.
                state = offer.snapshot() as DocumentState
                // Clear recentOperations after loading snapshot, as events before snapshot are not replayed
                recentOperations.clear()
                log.info("DocumentActor {}: Recovered from snapshot at sequence number {}. Version: {}", documentId, offer.metadata().sequenceNr(), state.version)
            }
            .match(OperationAppliedEvent::class.java) { event ->
                // Apply each replayed event to rebuild the document's state.
                state = state.copy(
                    content = event.transformedOperation.apply(state.content),
                    version = event.newVersion
                )
                // Optionally add recovered events to recent history if needed for very specific OT scenarios
                // where transformations might occur against very old recovered events.
                // For simplified OT, the primary goal here is to rebuild the latest state.
            }
            .matchAny { message ->
                log.warn("DocumentActor {} received unexpected message during recovery: {}", documentId, message)
            }
            .build()
    }

    // --- Command Handling Logic ---
    // This method defines how the actor processes incoming commands (messages) from other actors
    // or the outside world *after* recovery is complete.
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

    // --- Internal Logic for Processing Client Operations ---
    private fun processClientOperation(clientOp: ClientOperation, originalSender: ActorRef) {
        log.debug("DocumentActor {} received operation from client {}: {}", documentId, clientOp.clientId, clientOp.operation)

        // 1. Transform the incoming operation
        var transformedOp = clientOp.operation
        // Only transform if the client's version is older than the current server version
        if (clientOp.clientVersion < state.version) {
            val opsToTransformAgainst = getOperationsToTransformAgainst(clientOp.clientVersion)
            for (serverOpEvent in opsToTransformAgainst) {
                transformedOp = OtEngine.transform(transformedOp, serverOpEvent.transformedOperation)
                if (transformedOp is NoOp) {
                    log.info("DocumentActor {}: Client operation became NoOp after transformation. Original: {}", documentId, clientOp.operation)
                    break // No need to transform further if it's already a NoOp
                }
            }
            log.debug("DocumentActor {}: Transformed operation from {} to {}. Original version: {}, Current version: {}",
                documentId, clientOp.operation, transformedOp, clientOp.clientVersion, state.version)
        } else if (clientOp.clientVersion > state.version) {
            // This indicates a potential out-of-order or duplicate message from client.
            // In a real system, you'd handle this more robustly (e.g., buffering, dropping, error).
            // For now, log a warning and ignore.
            log.warn("DocumentActor {}: Client {} sent operation with future version {}. Current version {}. Ignoring for now.",
                documentId, clientOp.clientId, clientOp.clientVersion, state.version)
            return
        }
        // If clientOp.clientVersion == state.version, no transformation is needed as it's based on the current state.

        // 2. Persist the transformed operation as an event
        val newVersion = state.version + 1 // Increment version for the new state
        val event = OperationAppliedEvent(
            transformedOperation = transformedOp,
            newVersion = newVersion,
            originalClientId = clientOp.clientId,
            originalClientVersion = clientOp.clientVersion,
            originalRequestId = clientOp.requestId
        )

        // Persist the event. The code inside the lambda executes AFTER successful persistence.
        persist(event) { persistedEvent ->
            log.debug("DocumentActor {}: Event persisted: {}", documentId, persistedEvent)

            // 3. Apply the transformed operation to the current state (atomically after persist)
            state = state.copy(
                content = persistedEvent.transformedOperation.apply(state.content),
                version = persistedEvent.newVersion
            )
            log.info("DocumentActor {}: Document updated to version {}. Content length: {}", documentId, state.version, state.content.length)

            // Add to recent operations history (for future transformations). Keep history size bounded.
            recentOperations.add(persistedEvent)
            if (recentOperations.size > MAX_RECENT_OPS) {
                recentOperations.removeFirst()
            }

            // --- Optional: Periodically save snapshots to speed up recovery ---
            if (lastSequenceNr() % 100 == 0L) { // Example: save snapshot every 100 events
                saveSnapshot(state)
                log.info("DocumentActor {}: Saving snapshot at sequence number {}", documentId, lastSequenceNr())
            }

            // 4. Publish the DocumentUpdate to the ActorSystem's EventStream.
            // This allows other actors (like your WebSocket handler) to subscribe and receive updates.
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

    // Helper to get operations from history for transformation.
    // This should fetch events from recentOperations list that happened AFTER clientVersion.
    private fun getOperationsToTransformAgainst(clientVersion: Int): List<OperationAppliedEvent> {
        // Filter and sort to ensure operations are transformed in the correct order.
        return recentOperations.filter { it.newVersion > clientVersion }
            .sortedBy { it.newVersion }
    }
}