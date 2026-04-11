package app.m1k3.ai.assistant.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Animation mapping tests — verifies emotion/activity → animation selection.
 *
 * Key invariant: THINKING should NEVER map to Spin (conflicts with autoRotate).
 * Contemplative states get calm idles; active states get movement.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.95 | context: TDD for animation remap
 */
class AnimationMappingTest {

    // Typical Quirky Series animation set (shared across Omabuarts models)
    private val quirkAnimations = listOf(
        AnimationMetadata("Idle_A", 0, 2.0f, 60, true),
        AnimationMetadata("Idle_B", 1, 2.0f, 60, true),
        AnimationMetadata("Idle_C", 2, 2.0f, 60, true),
        AnimationMetadata("Fear", 3, 1.5f, 45, true),
        AnimationMetadata("Bounce", 4, 1.5f, 45, true),
        AnimationMetadata("Death", 5, 2.0f, 60, false),
        AnimationMetadata("Clicked", 6, 1.0f, 30, true),
        AnimationMetadata("Eat", 7, 2.0f, 60, true),
        AnimationMetadata("Sit", 8, 2.5f, 75, true),
        AnimationMetadata("Walk", 9, 1.5f, 45, true),
        AnimationMetadata("Run", 10, 1.0f, 30, true),
        AnimationMetadata("Fly", 11, 2.0f, 60, true),
        AnimationMetadata("Swim", 12, 2.0f, 60, true),
        AnimationMetadata("Attack", 13, 1.5f, 45, true),
        AnimationMetadata("Hit", 14, 1.0f, 30, false),
        AnimationMetadata("Roll", 15, 1.5f, 45, true),
        AnimationMetadata("Spin", 16, 2.0f, 60, true),
        AnimationMetadata("Jump", 17, 1.0f, 30, false)
    )

    // --- Activity → Animation tests ---

    @Test
    fun thinkingActivityMapsToIdleNotSpin() {
        val state = AvatarState(activity = AvatarActivity.THINKING)
        val anim = AnimationIntrospector.findAnimation(state, quirkAnimations)
        assertTrue(
            anim.name.contains("idle", ignoreCase = true) ||
                anim.name.contains("sit", ignoreCase = true),
            "THINKING should map to contemplative animation, got: ${anim.name}"
        )
    }

    @Test
    fun generatingActivityMapsToMovement() {
        val state = AvatarState(activity = AvatarActivity.GENERATING)
        val anim = AnimationIntrospector.findAnimation(state, quirkAnimations)
        assertTrue(
            anim.name.contains("walk", ignoreCase = true) ||
                anim.name.contains("run", ignoreCase = true) ||
                anim.name.contains("bounce", ignoreCase = true),
            "GENERATING should map to movement animation, got: ${anim.name}"
        )
    }

    @Test
    fun speakingActivityMapsToLively() {
        val state = AvatarState(activity = AvatarActivity.SPEAKING)
        val anim = AnimationIntrospector.findAnimation(state, quirkAnimations)
        assertTrue(
            anim.name.contains("bounce", ignoreCase = true) ||
                anim.name.contains("idle_b", ignoreCase = true) ||
                anim.name.contains("clicked", ignoreCase = true),
            "SPEAKING should map to lively animation, got: ${anim.name}"
        )
    }

    @Test
    fun listeningActivityMapsToCalm() {
        val state = AvatarState(activity = AvatarActivity.LISTENING)
        val anim = AnimationIntrospector.findAnimation(state, quirkAnimations)
        assertTrue(
            anim.name.contains("idle", ignoreCase = true) ||
                anim.name.contains("sit", ignoreCase = true),
            "LISTENING should map to calm animation, got: ${anim.name}"
        )
    }

    @Test
    fun errorActivityMapsToDistress() {
        val state = AvatarState(activity = AvatarActivity.ERROR)
        val anim = AnimationIntrospector.findAnimation(state, quirkAnimations)
        assertTrue(
            anim.name.contains("death", ignoreCase = true) ||
                anim.name.contains("fear", ignoreCase = true) ||
                anim.name.contains("hit", ignoreCase = true),
            "ERROR should map to distress animation, got: ${anim.name}"
        )
    }

    // --- Emotion → Animation: THINKING must NOT use Spin ---

    @Test
    fun thinkingEmotionDoesNotUseSpin() {
        val state = AvatarState(emotion = AvatarEmotion.THINKING, activity = AvatarActivity.IDLE)
        val anim = AnimationIntrospector.findAnimation(state, quirkAnimations)
        assertNotEquals("Spin", anim.name, "THINKING emotion should NOT use Spin (conflicts with autoRotate)")
    }

    // --- ProceduralAnimator amplitude tests ---

    @Test
    fun thinkingScaleAmplitudeIsSubtle() {
        val animator = ProceduralAnimator(AvatarState(activity = AvatarActivity.THINKING))
        assertTrue(
            animator.getScaleAmplitude() <= 0.05f,
            "THINKING amplitude should be <= 5%, got: ${animator.getScaleAmplitude()}"
        )
    }

    @Test
    fun generatingScaleAmplitudeIsModerate() {
        val animator = ProceduralAnimator(AvatarState(activity = AvatarActivity.GENERATING))
        assertTrue(
            animator.getScaleAmplitude() <= 0.08f,
            "GENERATING amplitude should be <= 8%, got: ${animator.getScaleAmplitude()}"
        )
    }

    @Test
    fun speakingScaleAmplitudeIsVisible() {
        val animator = ProceduralAnimator(AvatarState(activity = AvatarActivity.SPEAKING))
        val amp = animator.getScaleAmplitude()
        assertTrue(
            amp in 0.06f..0.10f,
            "SPEAKING amplitude should be 6-10%, got: $amp"
        )
    }

    // --- Generic API: Avatar3DEngine delegates to AnimationIntrospector ---

    @Test
    fun engineThinkingMapsToIdleA() {
        val anim = Avatar3DEngine.getAnimation(
            AvatarState(activity = AvatarActivity.THINKING),
            quirkAnimations
        )
        assertEquals("Idle_A", anim.name)
    }

    @Test
    fun engineGeneratingMapsToWalk() {
        val anim = Avatar3DEngine.getAnimation(
            AvatarState(activity = AvatarActivity.GENERATING),
            quirkAnimations
        )
        assertEquals("Walk", anim.name)
    }

    @Test
    fun engineWorksWithMinimalAnimationSet() {
        // Quaternius models may only have a few animations
        val sparseAnims = listOf(
            AnimationMetadata("Idle", 0, 2.0f, 60, true),
            AnimationMetadata("Walk", 1, 1.5f, 45, true),
            AnimationMetadata("Run", 2, 1.0f, 30, true),
        )
        val anim = Avatar3DEngine.getAnimation(
            AvatarState(activity = AvatarActivity.THINKING),
            sparseAnims
        )
        // Should find "Idle" via fuzzy match, not crash
        assertTrue(
            anim.name.contains("idle", ignoreCase = true) ||
                anim.name.contains("walk", ignoreCase = true),
            "Should gracefully handle sparse animation sets, got: ${anim.name}"
        )
    }

    // --- Speed scale ---

    @Test
    fun animationSpeedScaleIsCalm() {
        assertEquals(0.35f, Avatar3DEngine.ANIMATION_SPEED_SCALE)
    }
}
