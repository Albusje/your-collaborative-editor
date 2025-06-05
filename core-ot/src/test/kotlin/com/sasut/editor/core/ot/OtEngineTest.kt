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
            val opB = Insert(position = 2, text = "xyz") // B inserts before A
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 5 + 3, text = "abc")) // 3 is length of "xyz"
        }

        @Test
        @DisplayName("Case 3: Insert A at same position as Insert B (tie-breaking: A after B) -> A's position shifts right by B's length")
        fun `insertA_at_same_position_as_insertB_A_position_shifts`() {
            val opA = Insert(position = 2, text = "abc")
            val opB = Insert(position = 2, text = "xyz") // B inserts at same position as A
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 2 + 3, text = "abc")) // 3 is length of "xyz"
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
            val opB = Delete(position = 5, length = 3) // B deletes before A
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 10 - 3, text = "abc")) // 3 is length of deletion
        }

        @Test
        @DisplayName("Case 3: Insert A inside Delete B -> A's position moves to start of B's deletion")
        fun `insertA_inside_deleteB_A_position_moves_to_start`() {
            // Original: "abcdefg", opB deletes "cde" (pos 2, len 3) -> "abfg"
            // opA inserts "X" at pos 3 (e.g., 'd') -> "abcXdefg"
            // After opB: "abfg". Where should "X" go?
            // The logic: opA's position shifts to the beginning of the deleted segment.
            val opA = Insert(position = 3, text = "X") // Original: "a b c (X) d e f g"
            val opB = Delete(position = 2, length = 3) // Deletes "cde"
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Insert(position = 2, text = "X")) // Insert "X" at where 'c' was.
        }

        @Test
        @DisplayName("Case 4: Insert A overlaps Delete B (starts before, ends inside) -> A's position unchanged")
        fun `insertA_overlaps_deleteB_start_before_end_inside_A_position_unchanged`() {
            val opA = Insert(position = 1, text = "X") // insert 'X' at pos 1
            val opB = Delete(position = 2, length = 3) // delete 'cde'
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
            val opB = Insert(position = 2, text = "xyz") // B inserts before A
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 5 + 3, length = 3)) // 3 is length of "xyz"
        }

        @Test
        @DisplayName("Case 3: Insert B inside Delete A -> A's length increases by B's length")
        fun `insertB_inside_deleteA_A_length_increases`() {
            // Original: "abcdefg"
            // opA deletes "cde" (pos 2, len 3)
            // opB inserts "X" at pos 3 (e.g., 'd')
            // Result of opB then opA: opA should delete "cXde" where X was inserted.
            val opA = Delete(position = 2, length = 3) // Deletes "cde"
            val opB = Insert(position = 3, text = "X") // Inserts "X"
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 2, length = 3 + 1)) // 1 is length of "X"
        }

        @Test
        @DisplayName("Case 4: Delete A starts before, Insert B is at Delete A's start -> A's position shifts right by B's length, length adjusted")
        fun `deleteA_starts_before_insertB_at_start_A_position_shifts_right`() {
            val opA = Delete(position = 2, length = 5) // Deletes "cdefg"
            val opB = Insert(position = 2, text = "XY") // Inserts "XY" at 'c'
            // Expected: original delete (cdefg) now applies to "XYcdefg" after insert.
            // So, it should still delete from the same starting character's new position.
            // In "abXYcdefg", "cdefg" is now at index 4. The delete should start at 4 and have its length increased by 2.
            assertTransformed(op1 = opA, op2 = opB, expectedOp1Prime = Delete(position = 2 + 2, length = 5 + 2))
        }
    }
}