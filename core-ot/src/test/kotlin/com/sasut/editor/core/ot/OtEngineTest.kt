package com.sasut.editor.core.ot

import com.sasut.editor.core.model.Delete
import com.sasut.editor.core.model.Insert
import com.sasut.editor.core.model.NoOp
import com.sasut.editor.core.model.Operation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OtEngine Transformation Tests")
class OtEngineTest {

    // Helper function to make tests more readable
    private fun assertTransformed(op1: Operation, op2: Operation, expectedOp1Prime: Operation) {
        val result = OtEngine.transform(op1, op2)
        assertEquals(expectedOp1Prime, result)
    }

    @Nested
    @DisplayName("transformInsertInsert")
    inner class TransformInsertInsertTests {

        @Test
        @DisplayName("Case 1: Insert A before Insert B -> A unchanged")
        fun `insertA_before_insertB_A_unchanged`() {
            val opA = Insert(position = 2, text = "abc")
            val opB = Insert(position = 5, text = "xyz")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = opA)
        }

        @Test
        @DisplayName("Case 2: Insert A after Insert B -> A's position shifts right by B's length")
        fun `insertA_after_insertB_A_position_shifts`() {
            val opA = Insert(position = 5, text = "abc")
            val opB = Insert(position = 2, text = "xyz")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 8, text = "abc"))
        }

        @Test
        @DisplayName("Case 3: Insert A at same position as Insert B (tie-breaking: A before B) -> A's position unchanged")
        fun `insertA_at_same_position_as_insertB_A_position_unchanged`() {
            val opA = Insert(position = 2, text = "abc")
            val opB = Insert(position = 2, text = "xyz")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 2, text = "abc"))
        }
    }

    @Nested
    @DisplayName("transformInsertDelete")
    inner class TransformInsertDeleteTests {

        @Test
        @DisplayName("Case 1: Insert A before Delete B -> A unchanged")
        fun `insertA_before_deleteB_A_unchanged`() {
            val opA = Insert(position = 2, text = "abc")
            val opB = Delete(position = 5, length = 3)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = opA)
        }

        @Test
        @DisplayName("Case 2: Insert A after Delete B -> A's position shifts left by B's length")
        fun `insertA_after_deleteB_A_position_shifts_left`() {
            val opA = Insert(position = 10, text = "abc")
            val opB = Delete(position = 5, length = 3)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 7, text = "abc"))
        }

        @Test
        @DisplayName("Case 3: Insert A inside Delete B -> A's position moves to start of B's deletion")
        fun `insertA_inside_deleteB_A_position_moves_to_start`() {
            val opA = Insert(position = 3, text = "X")
            val opB = Delete(position = 2, length = 3)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 2, text = "X"))
        }

        @Test
        @DisplayName("Case 4: Insert A overlaps Delete B (starts before, ends inside) -> A's position unchanged")
        fun `insertA_overlaps_deleteB_start_before_end_inside_A_position_unchanged`() {
            val opA = Insert(position = 1, text = "X")
            val opB = Delete(position = 2, length = 3)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 1, text = "X"))
        }
    }

    @Nested
    @DisplayName("transformDeleteInsert")
    inner class TransformDeleteInsertTests {

        @Test
        @DisplayName("Case 1: Delete A before Insert B -> A unchanged")
        fun `deleteA_before_insertB_A_unchanged`() {
            val opA = Delete(position = 2, length = 3)
            val opB = Insert(position = 5, text = "xyz")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = opA)
        }

        @Test
        @DisplayName("Case 2: Delete A after Insert B -> A's position shifts right by B's length")
        fun `deleteA_after_insertB_A_position_shifts_right`() {
            val opA = Delete(position = 5, length = 3)
            val opB = Insert(position = 2, text = "xyz")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 8, length = 3))
        }

        @Test
        @DisplayName("Case 3: Insert B inside Delete A -> A's length increases by B's length")
        fun `insertB_inside_deleteA_A_length_increases`() {
            val opA = Delete(position = 2, length = 3)
            val opB = Insert(position = 3, text = "X")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 2, length = 4))
        }

        @Test
        @DisplayName("Case 4: Delete A starts before, Insert B is at Delete A's start -> A's position shifts right, length unchanged")
        fun `deleteA_starts_before_insertB_at_start_A_position_shifts_right`() {
            val opA = Delete(position = 2, length = 5)
            val opB = Insert(position = 2, text = "XY")
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 4, length = 5))
        }
    }

    @Nested
    @DisplayName("transformDeleteDelete")
    inner class TransformDeleteDeleteTests {

        @Test
        @DisplayName("Case 1: Delete A completely before Delete B -> A unchanged")
        fun `deleteA_before_deleteB_A_unchanged`() {
            val opA = Delete(position = 2, length = 3)
            val opB = Delete(position = 6, length = 2)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = opA)
        }

        @Test
        @DisplayName("Case 2: Delete A completely after Delete B -> A's position shifts left by B's length")
        fun `deleteA_after_deleteB_A_position_shifts_left`() {
            val opA = Delete(position = 6, length = 3)
            val opB = Delete(position = 2, length = 2)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 4, length = 3))
        }

        @Test
        @DisplayName("Case 3: Delete B completely contained within Delete A -> A's length reduces by B's length")
        fun `deleteB_contained_in_deleteA_A_length_reduces`() {
            val opA = Delete(position = 2, length = 6)
            val opB = Delete(position = 4, length = 2)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 2, length = 4))
        }

        @Test
        @DisplayName("Case 4: Delete A completely contained within Delete B -> A becomes NoOp")
        fun `deleteA_contained_in_deleteB_A_becomes_noop`() {
            val opA = Delete(position = 4, length = 2)
            val opB = Delete(position = 2, length = 6)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = NoOp)
        }

        @Test
        @DisplayName("Case 5: Delete A starts before Delete B, partial overlap -> A deletes only non-overlapping part")
        fun `deleteA_starts_before_deleteB_partial_overlap_A_keeps_non_overlap`() {
            val opA = Delete(position = 2, length = 4)
            val opB = Delete(position = 4, length = 4)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 2, length = 2))
        }

        @Test
        @DisplayName("Case 6: Delete B starts before Delete A, partial overlap -> A moves to B's start and deletes remaining")
        fun `deleteB_starts_before_deleteA_partial_overlap_A_moves_and_adjusts`() {
            val opA = Delete(position = 4, length = 4)
            val opB = Delete(position = 2, length = 4)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 2, length = 2))
        }

        @Test
        @DisplayName("Case 7: Delete A and Delete B are identical -> A becomes NoOp")
        fun `deleteA_identical_to_deleteB_A_becomes_noop`() {
            val opA = Delete(position = 3, length = 4)
            val opB = Delete(position = 3, length = 4)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = NoOp)
        }

        @Test
        @DisplayName("Edge case: Adjacent deletes (A ends where B starts) -> A unchanged")
        fun `deleteA_adjacent_to_deleteB_A_unchanged`() {
            val opA = Delete(position = 2, length = 3)
            val opB = Delete(position = 5, length = 2)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = opA)
        }

        @Test
        @DisplayName("Edge case: Overlap results in zero-length delete -> becomes NoOp")
        fun `overlap_results_in_zero_length_becomes_noop`() {
            val opA = Delete(position = 3, length = 2)
            val opB = Delete(position = 2, length = 4)
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = NoOp)
        }
    }
}