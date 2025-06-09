package com.sasut.editor.backend.state

// Represents the immutable state of a document maintained by DocumentActor
data class DocumentState(
    val content: String = "",
    val version: Int = 0 // The current version of the document
)