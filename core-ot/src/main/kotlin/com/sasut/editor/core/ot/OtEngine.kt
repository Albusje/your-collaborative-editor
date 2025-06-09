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
            op1 is NoOp || op2 is NoOp -> op1 // If op2 is NoOp, op1 is unchanged; if op1 is NoOp, it remains NoOp.
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
            else -> op1 // Tie-breaking: op1 appears before op2 (no position change)
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

        // Case 1: Insert occurs before the delete range
        if (op2Pos < op1Start) {
            // Delete position shifts right by insert length
            return Delete(op1Start + op2Length, op1.length)
        }
        // Case 2: Insert occurs at the exact start of delete range  
        else if (op2Pos == op1Start) {
            // Delete position shifts right, but length stays the same
            // (we don't want to delete the newly inserted text)
            return Delete(op1Start + op2Length, op1.length)
        }
        // Case 3: Insert occurs inside the delete range (but not at start)
        else if (op2Pos < op1End) {
            // Delete position unchanged, but length increases to include inserted text
            return Delete(op1Start, op1.length + op2Length)
        }
        // Case 4: Insert occurs after the delete range
        else {
            // Delete operation is unaffected
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

        // Case 1: op1 is completely before op2
        if (op1End <= op2Start) {
            // op1 is unaffected by op2
            return op1
        }
        
        // Case 2: op1 is completely after op2
        if (op1Start >= op2End) {
            // op1's position shifts left by op2's length
            return Delete(op1Start - op2.length, op1.length)
        }
        
        // Case 3: op2 is completely contained within op1
        if (op1Start <= op2Start && op1End >= op2End) {
            // op1's length reduces by op2's length (the overlapping part is already deleted by op2)
            val newLength = op1.length - op2.length
            return if (newLength > 0) Delete(op1Start, newLength) else NoOp
        }
        
        // Case 4: op1 is completely contained within op2
        if (op2Start <= op1Start && op2End >= op1End) {
            // op1 becomes a NoOp because everything it wanted to delete was already deleted by op2
            return NoOp
        }
        
        // Case 5: op1 and op2 overlap partially - op1 starts before op2, but they overlap
        if (op1Start < op2Start && op1End > op2Start && op1End <= op2End) {
            // op1 deletes only the part before op2 starts
            val newLength = op2Start - op1Start
            return if (newLength > 0) Delete(op1Start, newLength) else NoOp
        }
        
        // Case 6: op1 and op2 overlap partially - op2 starts before op1, but they overlap  
        if (op2Start < op1Start && op2End > op1Start && op2End < op1End) {
            // op1 position moves to where op2 ends (adjusted for op2's deletion)
            // and length is reduced by the overlapping part
            val overlapLength = op2End - op1Start
            val newPosition = op2Start // op1 now starts where op2 started
            val newLength = op1.length - overlapLength
            return if (newLength > 0) Delete(newPosition, newLength) else NoOp
        }
        
        // Case 7: op1 and op2 are identical
        if (op1Start == op2Start && op1.length == op2.length) {
            // op1 becomes NoOp since op2 already deleted the same content
            return NoOp
        }
        
        // Fallback (should not reach here with correct logic above)
        return op1
    }
}