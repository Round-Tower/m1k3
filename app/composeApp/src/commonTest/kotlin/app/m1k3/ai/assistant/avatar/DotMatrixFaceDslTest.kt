package app.m1k3.ai.assistant.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * DotMatrixFaceDsl — region-composition authoring for the 64×64 hero face.
 *
 * Every `AvatarEmotion` maps to a `Face` (eyes + mouth + brow + extras), and
 * rasterizes to a 4096-cell BooleanArray. These tests lock in the invariants
 * a future editor needs so they can swap region art without breaking shape.
 *
 * Signed: kev + claude | confidence: 0.78 | context: hero dot-matrix
 */
class DotMatrixFaceDslTest {
    @Test
    fun `every emotion rasterizes to 4096 cells`() {
        for (emotion in AvatarEmotion.entries) {
            val bits = faceFor(emotion, blink = false).rasterize()
            assertEquals(4096, bits.size, "$emotion wrong size")
        }
    }

    @Test
    fun `happy and sad produce different bitmaps`() {
        val happy = faceFor(AvatarEmotion.HAPPY, blink = false).rasterize()
        val sad = faceFor(AvatarEmotion.SAD, blink = false).rasterize()
        assertNotEquals(happy.toList(), sad.toList(), "HAPPY == SAD bitmap")
    }

    @Test
    fun `blink forces closed eyes regardless of emotion`() {
        for (emotion in AvatarEmotion.entries) {
            val face = faceFor(emotion, blink = true)
            assertEquals(EyeStyle.CLOSED, face.eyes, "$emotion did not blink")
        }
    }

    @Test
    fun `head outline is lit for every emotion`() {
        // Every face must show the rounded-rect head outline. Sample a handful
        // of outline cells that should always be on.
        val outlineSamples =
            listOf(
                4 + 20 * 64, // left border, upper band
                59 + 20 * 64, // right border
                30 + 4 * 64, // top border, middle
                30 + 59 * 64, // bottom border, middle
            )
        for (emotion in AvatarEmotion.entries) {
            val bits = faceFor(emotion, blink = false).rasterize()
            for (idx in outlineSamples) {
                assertTrue(bits[idx], "$emotion missing outline cell at index $idx")
            }
        }
    }

    @Test
    fun `eye region responds to emotion change`() {
        // Compare the eye row at row=26, cols 14..27 between NEUTRAL and SLEEPY.
        // They should not be identical — SLEEPY closes the eyes.
        val neutral = faceFor(AvatarEmotion.NEUTRAL, blink = false).rasterize()
        val sleepy = faceFor(AvatarEmotion.SLEEPY, blink = false).rasterize()
        val row = 26
        val sameRow =
            (14..27).all { col ->
                neutral[col + row * 64] == sleepy[col + row * 64]
            }
        assertTrue(!sameRow, "NEUTRAL and SLEEPY have identical eye row 26")
    }

    @Test
    fun `rasterize is deterministic`() {
        val a = faceFor(AvatarEmotion.THINKING, blink = false).rasterize()
        val b = faceFor(AvatarEmotion.THINKING, blink = false).rasterize()
        assertEquals(a.toList(), b.toList(), "non-deterministic rasterization")
    }
}
