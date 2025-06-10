package com.sasut.editor.core.model
// Data for operations (Insert, Delete, etc.)

import java.io.Serializable

// Represents a base operation type
sealed interface Operation : Serializable {
    /**
     * Applies this operation to a given document string.
     * @param document The string document to apply the operation to.
     * @return The new document string after applying the operation.
     */
    fun apply(document: String): String
}

// Represents an insertion operation
data class Insert(val position: Int, val text: String) : Operation {
    init {
        require(position >= 0) { "Insert position must be non-negative." }
        require(text.isNotEmpty()) { "Insert text cannot be empty." }
    }

    override fun apply(document: String): String {
        require(position <= document.length) { "Insert position $position out of bounds for document length ${document.length}" }
        return document.substring(0, position) + text + document.substring(position)
    }
}

// Represents a deletion operation
data class Delete(val position: Int, val length: Int) : Operation {
    init {
        require(position >= 0) { "Delete position must be non-negative." }
        require(length > 0) { "Delete length must be positive." }
    }

    override fun apply(document: String): String {
        require(position + length <= document.length) { "Delete range out of bounds for document length ${document.length}" }
        return document.substring(0, position) + document.substring(position + length)
    }
}

object NoOp : Operation {
    override fun apply(document: String): String = document
}