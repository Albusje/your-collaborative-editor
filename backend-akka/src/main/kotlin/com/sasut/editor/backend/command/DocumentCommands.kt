package com.sasut.editor.backend.command

import com.sasut.editor.core.model.Operation
import akka.actor.ActorRef

// Represents an operation coming from a client
data class ClientOperation(
    val operation: Operation,
    val clientId: String,
    val clientVersion: Int, // The version of the document the client based its operation on
    val requestId: String // Unique ID for this client request, for idempotence/tracking
)

// Message to request the current state of a document (Already in your code)
data class GetDocumentState(val requestId: String)

// Message for internal use or to return state to a requester (Already in your code)
data class DocumentStateResponse(
    val documentId: String,
    val content: String,
    val version: Int
)

// === ADD THIS CLASS (DocumentActorRefResponse) ===
// Message to request an ActorRef for a specific document from DocumentManagerActor
data class GetDocumentActor(val documentId: String, val requestId: String = "")

// Message to respond with the requested DocumentActor's ActorRef
data class DocumentActorRefResponse(
    val documentId: String,
    val actorRef: ActorRef?, // ActorRef from Akka
    val requestId: String = ""
)
// === END ADDITION ===