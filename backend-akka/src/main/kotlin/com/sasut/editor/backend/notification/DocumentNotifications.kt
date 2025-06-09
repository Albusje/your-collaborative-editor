package com.sasut.editor.backend.notification

import com.sasut.editor.core.model.Operation
import java.io.Serializable // Essential for Akka EventStream or Cluster Pub/Sub

data class DocumentUpdate(
    val documentId: String,
    val transformedOperation: Operation,
    val newVersion: Int,
    val updatedContent: String // Send full content for easier client-side sync
) : Serializable