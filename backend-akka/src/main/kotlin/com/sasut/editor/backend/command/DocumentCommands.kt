package com.sasut.editor.backend.command

import com.sasut.editor.core.model.Operation

// Represents an operation coming from a client
data class ClientOperation(
    val operation: Operation,
    val clientId: String,
    val clientVersion: Int, // The version of the document the client based its operation on
    val requestId: String // Unique ID for this client request, for idempotence/tracking
)

// Message to request the current state of a document
data class GetDocumentState(val requestId: String)

// Message for internal use or to return state to a requester
data class DocumentStateResponse(
    val documentId: String,
    val content: String,
    val version: Int
)