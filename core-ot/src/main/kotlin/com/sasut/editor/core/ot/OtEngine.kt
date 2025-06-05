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

    /**
     * Composes two operations [op1] and [op2] into a single operation.
     * Applying the composed operation should yield the same result as applying [op1] then [op2].
     * This can be useful for reducing the number of operations, though it's optional for core OT.
     *
     * @param op1 The first operation.
     * @param op2 The second operation to compose with [op1].
     * @return A single operation that is equivalent to applying [op1] then [op2].
     */
    fun compose(op1: Operation, op2: Operation): Operation {
        // This is more complex and depends on specific scenarios.
        // For simplicity, you might initially just throw an error or not implement if not needed
        // For example, if op1 is Insert(p1, t1) and op2 is Insert(p2, t2)
        // If p1 <= p2, the new op will be Insert(p1, t1) + Insert(p2 + t1.length, t2)
        // This is usually done by applying op1 to an empty string, then op2 to the result,
        // and generating a single operation from the changes.
        // A simpler approach for this project might be to just apply operations sequentially without composing.
        // If you need to implement this, it's often done by converting ops to a common delta format.
        throw UnsupportedOperationException("Compose is not implemented for this simplified example.")
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
        // Case 1: Insert is before delete. Shift delete position right.
        if (op2.position <= op1.position) {
            return op1.copy(position = op1.position + op2.text.length)
        }
        // Case 2: Insert is inside delete range. Extend delete length.
        if (op2.position < op1.position + op1.length) {
            // This is complex: the insert expands the document that the delete would operate on.
            // The delete range needs to be adjusted to include the inserted text.
            // Example: "abc", op1=Delete(1,1) (deletes 'b'). op2=Insert(1,"X") (inserts 'X' -> "aXbc")
            // The original delete intended to delete 'b'. In the new "aXbc", 'b' is now at index 2.
            // So op1 becomes Delete(2,1).
            // This rule makes the delete delete 'X' if inserted before it, which might not be desired.
            // A simpler approach for the transformDeleteInsert often involves splitting the delete or adjusting its range.
            // Let's assume a simpler case where the delete position and length are just adjusted.
            // If the insert is within the delete, the delete's length might increase.
            return op1.copy(length = op1.length + op2.text.length)
        }
        // Case 3: Insert is after delete. No change needed for delete.
        return op1
    }

    // Handles the transformation of a Delete operation against another Delete operation.
    // If the delete regions overlap or are adjacent, the operations need careful adjustment.
    private fun transformDeleteDelete(op1: Delete, op2: Delete): Operation {
        // This is perhaps the most complex case as deletions can reduce the document length
        // and shift subsequent operations.
        // The goal is: what does op1 delete, given that op2 has *already* deleted its part?

        val op1Start = op1.position
        val op1End = op1.position + op1.length
        val op2Start = op2.position
        val op2End = op2.position + op2.length

        // Case 1: op1 is entirely before op2. Shift op1's position left by op2's length.
        if (op1End <= op2Start) {
            return op1
        }
        // Case 2: op1 is entirely after op2. Shift op1's position left by op2's length.
        if (op1Start >= op2End) {
            return op1.copy(position = op1.position - op2.length)
        }

        // Case 3: op1 overlaps or contains op2.
        // This is where it gets tricky. We need to find what part of op1 is NOT deleted by op2.

        // Calculate the effective start of op1 after op2's deletion.
        val newOp1Start = op1Start - (if (op1Start > op2Start) minOf(op1Start, op2End) - op2Start else 0)
        // Calculate the effective end of op1 after op2's deletion.
        val newOp1End = op1End - (if (op1End > op2Start) minOf(op1End, op2End) - op2Start else 0)

        val newLength = newOp1End - newOp1Start

        return if (newLength > 0) {
            Delete(newOp1Start, newLength)
        } else {
            NoOp // The deletion has no effect after op2
        }
    }
}