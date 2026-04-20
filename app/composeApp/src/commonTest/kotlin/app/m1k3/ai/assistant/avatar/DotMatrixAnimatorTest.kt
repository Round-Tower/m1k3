package app.m1k3.ai.assistant.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DotMatrixAnimator — diagonal-sweep stagger for 64×64 LED flip.
 *
 * Invariants:
 *  - progress=0 ⇒ every cell at 0 (sprite fully "prev")
 *  - progress=1 ⇒ every cell at 1 (sprite fully "curr")
 *  - Top-left leads bottom-right (diagonal sweep direction)
 *  - Monotonic per cell as progress increases
 *
 * Signed: kev + claude | confidence: 0.78 | context: hero dot-matrix TDD
 */
class DotMatrixAnimatorTest {
    @Test
    fun `progress 0 yields 0 for every cell`() {
        for (row in 0..63) {
            for (col in 0..63) {
                assertEquals(
                    0f,
                    DotMatrixAnimator.cellProgress(col, row, 0f),
                    "cell($col,$row) @ p=0",
                )
            }
        }
    }

    @Test
    fun `progress 1 yields 1 for every cell`() {
        for (row in 0..63) {
            for (col in 0..63) {
                assertEquals(
                    1f,
                    DotMatrixAnimator.cellProgress(col, row, 1f),
                    absoluteTolerance = 0.0005f,
                    message = "cell($col,$row) @ p=1",
                )
            }
        }
    }

    @Test
    fun `top-left leads bottom-right at midpoint`() {
        val tl = DotMatrixAnimator.cellProgress(0, 0, 0.5f)
        val br = DotMatrixAnimator.cellProgress(63, 63, 0.5f)
        assertTrue(tl > br, "top-left ($tl) should lead bottom-right ($br) mid-sweep")
    }

    @Test
    fun `per cell progress is monotonic in time`() {
        val samples = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)
        for (row in listOf(0, 31, 63)) {
            for (col in listOf(0, 31, 63)) {
                var last = -Float.MAX_VALUE
                for (p in samples) {
                    val v = DotMatrixAnimator.cellProgress(col, row, p)
                    assertTrue(v >= last, "cell($col,$row) regressed $last → $v @ p=$p")
                    last = v
                }
            }
        }
    }

    @Test
    fun `cells on same diagonal progress together`() {
        // col+row is the stagger axis; equal diagonals → equal progress
        val a = DotMatrixAnimator.cellProgress(10, 20, 0.4f)
        val b = DotMatrixAnimator.cellProgress(20, 10, 0.4f)
        val c = DotMatrixAnimator.cellProgress(0, 30, 0.4f)
        assertEquals(a, b, absoluteTolerance = 0.0001f, message = "equal diagonals differ (a=$a b=$b)")
        assertEquals(a, c, absoluteTolerance = 0.0001f, message = "equal diagonals differ (a=$a c=$c)")
    }
}
