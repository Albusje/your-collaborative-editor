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
            op1 is NoOp || op2 is NoOp -> op1 // If op2 is NoOp, op1 is unchanged; if op1 is NoOp, it remains NoOp.
            else -> throw IllegalArgumentException("Unsupported operation types for transformation: $op1 vs $op2")
        }
    }

    // --- Private Transformation Helper Functions ---

    // Handles the transformation of an Insert operation against another Insert operation.
    // If op1.position < op2.position, op1 is unaffected.
    // If op1.position > op2.position, op1.position is shifted by op2.text.length.
    // If op1.position == op2.position, a tie-breaking rule is applied (e.g., op1 always comes after op2).
    private fun transformInsertInsert(op1: Insert, op2: Insert): Operation {
        return when {
            op1.position < op2.position -> op1
            op1.position > op2.position -> op1.copy(position = op1.position + op2.text.length)
            else -> op1.copy(position = op1.position + op2.text.length) // Tie-breaking: op1 appears after op2
        }
    }

    // Handles the transformation of an Insert operation against a Delete operation.
    // If the insert position is after the deleted range, it's shifted left.
    // If the insert position is within or before the deleted range, it might be truncated or unaffected.
    private fun transformInsertDelete(op1: Insert, op2: Delete): Operation {
        // Case 1: Insert is entirely before delete.
        if (op1.position <= op2.position) {
            return op1
        }
        // Case 2: Insert is entirely after delete.
        if (op1.position >= op2.position + op2.length) {
            return op1.copy(position = op1.position - op2.length)
        }
        // Case 3: Insert is within the deleted region. This insert essentially disappears.
        // Or, more accurately, it's applied *before* the deletion but then is deleted.
        // For a client-side op transforming against a server-side delete:
        // The client's intent to insert was on a state before the deletion.
        // After the deletion, that position might be gone or shifted.
        // Simplest rule: if inserted text is deleted, it's effectively a NoOp from the server's perspective.
        // However, this is more nuanced. Often, you transform 'through' the deletion.
        // For text editors, an insert inside a deleted region usually means the insert moves to the start of the deletion.
        return op1.copy(position = op2.position) // Move insert to the start of the deleted range
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

        // if insert(op2) occurs before or at the start od the delete(op1)
        // the delete position and possibly the length will shift right.
        if (op2Pos <= op1Start){
            // If op2 is entirely before op1, or at op1's start:
            // Delete position shifts right by op2's length.
            // Example: Doc "abcde", op1=Del(2,2) -> "abe". op2=Ins(1,"X") -> "aXcde"
            // Transformed op1: Del(3,2) -> "aXbe" (deletes 'c' 'd')
            val newPosition = op1Start + op2Length
            val newLenght = op1.length // Length itself doesn't change relative to the inserted text if it's outside.

            // However, if the insert is within the delete range, the delete's length must also expand.
            // If op2Pos is within op1's original range (or exactly at op1Start), then op1's length should expand
            // to cover the newly inserted text.
            if (op2Pos >= op1Start && op2Pos < op1End) { // Insert is inside the delete range or exactly at its start.
                return Delete(newPosition, op1.length + op2Length)
            }
            return Delete(newPosition, op1.length)
        }
        // If the insert (op2) occurs inside the delete (op1) but not at its very start.
        // The delete's position remains unchanged, but its length extends to cover the inserted text.
        else if (op2Pos < op1End) { // op2Pos > op1Start && op2Pos < op1End
            // Insert is strictly inside the delete range.
            // Example: Doc "abcde", op1=Del(1,3) ('bcd'). op2=Ins(2,"X") ('c') -> "abXce"
            // Transformed op1: Del(1,4) ('bXcd') (original 'b', then 'X', then 'c', 'd')
            return Delete(op1Start, op1.length + op2Length)
        }
        // If the insert (op2) occurs after the delete (op1).
        // The delete operation is unaffected.
        else { // op2Pos >= op1End
            return op1
        }
    }
}