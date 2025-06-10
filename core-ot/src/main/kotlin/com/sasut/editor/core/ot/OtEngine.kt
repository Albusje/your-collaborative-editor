package com.sasut.editor.core.ot

import com.sasut.editor.core.model.*

/**
 * OtEngine provides the core Operational Transformation algorithms.
 * It ensures that concurrent operations on a document converge to the same state.
 */
object OtEngine {

    /**
     * Transforms an operation [op1] against another operation [op2].
     * This function is crucial for real-time collaboration. When a client sends [op1],
     * and the server has already applied [op2] (which the client hadn't seen),
     * [op1] must be transformed so that it can be applied to the document *after* [op2],
     * while still preserving the original intent of [op1].
     *
     * @param op1 The operation to transform.
     * @param op2 The operation that has already been applied (or will be applied before op1).
     * @return The transformed operation [op1]'.
     */
    fun transform(op1: Operation, op2: Operation): Operation {
        return when {
            op1 is Insert && op2 is Insert -> transformInsertInsert(op1, op2)
            op1 is Insert && op2 is Delete -> transformInsertDelete(op1, op2)
            op1 is Delete && op2 is Insert -> transformDeleteInsert(op1, op2)
            op1 is Delete && op2 is Delete -> transformDeleteDelete(op1, op2)
            op1 is NoOp || op2 is NoOp -> op1
            else -> throw IllegalArgumentException("Unsupported operation types for transformation: $op1 vs $op2")
        }
    }

    // --- Private Transformation Helper Functions ---

    // Handles the transformation of an Insert operation against another Insert operation.
    // If op1.position < op2.position, op1 is unaffected.
    // If op1.position > op2.position, op1.position is shifted by op2.text.length.
    // If op1.position == op2.position, a tie-breaking rule is applied (op1 goes before op2).
    private fun transformInsertInsert(op1: Insert, op2: Insert): Operation {
        return when {
            op1.position < op2.position -> op1
            op1.position > op2.position -> op1.copy(position = op1.position + op2.text.length)
            else -> op1 // Tie-breaking: op1 appears before op2
        }
    }

    // Handles the transformation of an Insert operation against a Delete operation.
    // If the insert position is after the deleted range, it's shifted left.
    // If the insert position is within or before the deleted range, it might be truncated or unaffected.
    private fun transformInsertDelete(op1: Insert, op2: Delete): Operation {
        if (op1.position <= op2.position) {
            return op1
        }
        if (op1.position >= op2.position + op2.length) {
            return op1.copy(position = op1.position - op2.length)
        }
        // Insert is within the deleted region - move to start of deletion
        return op1.copy(position = op2.position)
    }

    // Handles the transformation of a Delete operation against an Insert operation.
    // If the delete position is before the insert, it's unaffected.
    // If the delete position is after the insert, it's shifted right.
    // If the insert is within the deleted range, the delete needs to be adjusted.
    private fun transformDeleteInsert(op1: Delete, op2: Insert): Operation {
        val op1Start = op1.position
        val op1End = op1.position + op1.length
        val op2Pos = op2.position
        val op2Length = op2.text.length

        if (op2Pos < op1Start) {
            return Delete(op1Start + op2Length, op1.length)
        } else if (op2Pos == op1Start) {
            return Delete(op1Start + op2Length, op1.length)
        } else if (op2Pos < op1End) {
            return Delete(op1Start, op1.length + op2Length)
        } else {
            return op1
        }
    }

    // Handles the transformation of a Delete operation against another Delete operation.
    // This is the most complex case as deletes can overlap in various ways.
    private fun transformDeleteDelete(op1: Delete, op2: Delete): Operation {
        val op1Start = op1.position
        val op1End = op1.position + op1.length
        val op2Start = op2.position
        val op2End = op2.position + op2.length

        if (op1End <= op2Start) {
            return op1
        }
        
        if (op1Start >= op2End) {
            return Delete(op1Start - op2.length, op1.length)
        }
        
        if (op1Start <= op2Start && op1End >= op2End) {
            val newLength = op1.length - op2.length
            return if (newLength > 0) Delete(op1Start, newLength) else NoOp
        }
        
        if (op2Start <= op1Start && op2End >= op1End) {
            return NoOp
        }
        
        if (op1Start < op2Start && op1End > op2Start && op1End <= op2End) {
            val newLength = op2Start - op1Start
            return if (newLength > 0) Delete(op1Start, newLength) else NoOp
        }
        
        if (op2Start < op1Start && op2End > op1Start && op2End < op1End) {
            val overlapLength = op2End - op1Start
            val newPosition = op2Start
            val newLength = op1.length - overlapLength
            return if (newLength > 0) Delete(newPosition, newLength) else NoOp
        }
        
        if (op1Start == op2Start && op1.length == op2.length) {
            return NoOp
        }
        
        return op1
    }
}