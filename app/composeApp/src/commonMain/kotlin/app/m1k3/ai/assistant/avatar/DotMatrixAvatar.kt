package app.m1k3.ai.assistant.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import app.m1k3.ai.assistant.design.tokens.MaColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * LED dot-matrix hero mascot — 64×64 orange dots on charcoal.
 *
 * A tangible, mechanical-feeling face. Emotion changes trigger a staggered
 * diagonal flip sweep (top-left first → bottom-right last). Idles breathe
 * with a subtle scale, blink every 4.5–6s, and expose their "mood" through
 * facial shape (not color — the dots stay M1K3 orange).
 *
 * Activity ring: when `state.activity.isActive`, four larger dots chase
 * around the grid perimeter at a colour hinting at the current activity
 * (thinking / generating / listening / speaking).
 *
 * Pure Compose Canvas — no platform dependencies, works on iOS day one.
 *
 * Signed: kev + claude | confidence: 0.75 | context: hero identity change,
 * deliberately replaces 3D Omabuarts default with a pixel-native mascot
 * that speaks the same language as Silkscreen + AMOLED charcoal.
 */
@Composable
fun DotMatrixAvatar(
    state: AvatarState,
    modifier: Modifier = Modifier,
) {
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(4500L, 6000L))
            blink = true
            delay(120L)
            blink = false
        }
    }

    val targetKey = state.emotion to blink
    val targetBits = remember(targetKey) { faceFor(state.emotion, blink).rasterize() }
    var prevBits by remember { mutableStateOf(targetBits) }
    val anim = remember { Animatable(1f) }

    LaunchedEffect(targetKey) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis = 650, easing = LinearEasing))
        prevBits = targetBits
    }

    // Only cells that are lit in prev OR target need to be touched each frame.
    // Collapses the 4096-cell loop to the union of lit cells (typically 400-900)
    // which keeps draw-call count under the 16ms frame budget on Pixel 9a.
    val workingCells =
        remember(prevBits, targetBits) {
            IntArray(prevBits.size) { it }
                .filter { prevBits[it] || targetBits[it] }
                .toIntArray()
        }

    val infinite = rememberInfiniteTransition(label = "dotmatrix")
    val breathe by infinite.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.02f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "breathe",
    )
    val chase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "chase",
    )

    val dotOn = MaColors.Orange
    val dotOff = MaColors.Orange.copy(alpha = 0.05f)
    val glow = MaColors.Orange.copy(alpha = 0.18f)
    val activityTint =
        when (state.activity) {
            AvatarActivity.LISTENING -> MaColors.Info
            AvatarActivity.THINKING -> MaColors.Warning
            AvatarActivity.GENERATING -> MaColors.Orange
            AvatarActivity.SPEAKING -> MaColors.Success
            AvatarActivity.ERROR -> MaColors.Error
            AvatarActivity.IDLE -> dotOff
        }

    val progress = anim.value
    val isActive = state.activity.isActive

    Canvas(
        modifier =
            modifier.graphicsLayer {
                scaleX = breathe
                scaleY = breathe
            },
    ) {
        val gridPx = minOf(size.width, size.height)
        val cellSize = gridPx / DOT_GRID.toFloat()
        val dotRadius = cellSize * 0.42f
        val glowRadius = dotRadius * 1.5f
        val originX = (size.width - gridPx) / 2f
        val originY = (size.height - gridPx) / 2f

        for (idx in workingCells) {
            val col = idx % DOT_GRID
            val row = idx / DOT_GRID
            val wasOn = prevBits[idx]
            val willBeOn = targetBits[idx]
            val p = DotMatrixAnimator.cellProgress(col, row, progress)
            val lit = if (wasOn == willBeOn) wasOn else p >= 0.5f
            if (!lit) continue

            val cx = originX + col * cellSize + cellSize / 2f
            val cy = originY + row * cellSize + cellSize / 2f
            val transitioning = wasOn != willBeOn
            val scale = if (transitioning) sin(p * PI).toFloat().coerceAtLeast(0.35f) else 1f
            if (transitioning) {
                drawCircle(color = glow, radius = glowRadius * scale, center = Offset(cx, cy))
            }
            drawCircle(color = dotOn, radius = dotRadius * scale, center = Offset(cx, cy))
        }

        if (isActive) {
            val perim = 4 * (DOT_GRID - 1)
            repeat(4) { i ->
                val t = ((chase + i * 0.25f) % 1f)
                val step = (t * perim).toInt()
                val (col, row) = perimeterCell(step)
                val cx = originX + col * cellSize + cellSize / 2f
                val cy = originY + row * cellSize + cellSize / 2f
                drawCircle(color = activityTint, radius = dotRadius * 1.3f, center = Offset(cx, cy))
                drawCircle(
                    color = activityTint.copy(alpha = 0.3f),
                    radius = dotRadius * 2.4f,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/**
 * Walks the 64×64 grid perimeter as a single 1D index.
 * 0 … 63 = top row (L→R), then right col (T→B), bottom (R→L), left (B→T).
 */
private fun perimeterCell(step: Int): Pair<Int, Int> {
    val side = DOT_GRID
    val s = step.mod(4 * (side - 1))
    return when {
        s < side - 1 -> s to 0

        // top, L→R
        s < 2 * (side - 1) -> (side - 1) to (s - (side - 1))

        // right, T→B
        s < 3 * (side - 1) -> (3 * (side - 1) - s) to (side - 1)

        // bottom, R→L
        else -> 0 to (4 * (side - 1) - s) // left, B→T
    }
}
