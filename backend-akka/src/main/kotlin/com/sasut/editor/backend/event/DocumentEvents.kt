package com.sasut.editor.backend.event

import com.sasut.editor.core.model.Operation
import java.io.Serializable // Akka Persistence events should generally be Serializable

// Event recorded when an operation is applied to the document
data class OperationAppliedEvent(
    val transformedOperation: Operation, // The operation after server-side transformation
    val newVersion: Int,
    val originalClientId: String, // The client that initiated this operation
    val originalClientVersion: Int, // The version the client was at
    val originalRequestId: String // For correlating with client requests
) : Serializable // Mark as Serializable for Akka Persistence