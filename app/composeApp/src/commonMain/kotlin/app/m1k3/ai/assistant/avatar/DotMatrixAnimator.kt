package app.m1k3.ai.assistant.avatar

/**
 * Diagonal-sweep stagger math for the 64×64 dot-matrix face.
 *
 * When the avatar's emotion changes, every cell doesn't flip at once — the
 * transition sweeps from top-left to bottom-right like a mechanical flipboard.
 * This object is pure math so the animation behaviour can be unit-tested
 * without pulling in Compose or platform code.
 *
 * Cell progress is on [0..1]. At 0 the cell shows the *previous* sprite state,
 * at 1 it shows the *current* sprite state, mid-values drive a flip animation
 * (scale pop, glow bloom) in the renderer.
 *
 * Signed: kev + claude | confidence: 0.78 | context: hero dot-matrix
 */
object DotMatrixAnimator {
    /**
     * The stagger math. For a 64×64 grid the maximum diagonal is 63+63=126.
     *
     * We want:
     *  - cell(0,0) to finish its flip exactly when `progress=windowFrac`
     *  - cell(63,63) to start its flip exactly when `progress=1-windowFrac`
     *  - Both constraints satisfied means: `spread = 1 - windowFrac`
     *  - And: `offsetPerDiag = spread / maxDiag = (1 - windowFrac) / 126`
     *
     * With `windowFrac = 0.4` → per-cell flip takes 40% of the total animation,
     * and the diagonal sweep accounts for the other 60%. This gives a visibly
     * staggered wave without any cell feeling sluggish.
     */
    private const val WINDOW_FRAC = 0.4f
    private const val MAX_DIAG = 126 // 63 + 63
    private const val OFFSET_PER_DIAG = (1f - WINDOW_FRAC) / MAX_DIAG

    fun cellProgress(
        col: Int,
        row: Int,
        progress: Float,
    ): Float {
        val diag = (col + row).coerceIn(0, MAX_DIAG)
        val start = diag * OFFSET_PER_DIAG
        val local = (progress - start) / WINDOW_FRAC
        return local.coerceIn(0f, 1f)
    }
}
