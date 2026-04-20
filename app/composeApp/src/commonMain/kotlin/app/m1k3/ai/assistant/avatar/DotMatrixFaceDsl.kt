package app.m1k3.ai.assistant.avatar

/**
 * Region-composition DSL for the 64×64 dot-matrix hero face.
 *
 * Rather than hand-painting 4,096 bits per emotion, a [Face] picks a combination
 * of small region sprites — [EyeStyle], [MouthStyle], [BrowStyle], and optional
 * [Extra] point sprites — which rasterize onto a shared 64×64 BooleanArray.
 *
 * Each rasterised cell is `true` (lit dot) or `false` (dim dot). The renderer
 * turns that into orange circles on AMOLED charcoal.
 *
 * Adding a new emotion = a new branch in [faceFor] plus (optionally) a new
 * region-draw function. That's the whole authoring surface.
 *
 * Signed: kev + claude | confidence: 0.78 | context: hero dot-matrix
 */

const val DOT_GRID = 64
private const val GRID_SIZE = DOT_GRID * DOT_GRID

enum class EyeStyle {
    SQUARE,
    ARCH_UP,
    ARCH_DOWN,
    CLOSED,
    WIDE_O,
    HEART,
    SLEEPY_DASH,
    DOTS,
}

enum class MouthStyle {
    STRAIGHT,
    SMILE_SM,
    SMILE_LG,
    FROWN,
    O,
    TILDE,
    DOTS_THINKING,
}

enum class BrowStyle {
    NONE,
    ANGRY,
    SAD,
    CURIOUS,
}

enum class Extra {
    TEAR_LEFT,
    SPARKLE_TL,
    SPARKLE_TR,
    SPARKLE_BL,
    SPARKLE_BR,
    HEART_CHEEK_LEFT,
    HEART_CHEEK_RIGHT,
}

data class Face(
    val eyes: EyeStyle,
    val mouth: MouthStyle,
    val brow: BrowStyle = BrowStyle.NONE,
    val extras: List<Extra> = emptyList(),
)

fun faceFor(
    emotion: AvatarEmotion,
    blink: Boolean,
): Face {
    val base =
        when (emotion) {
            AvatarEmotion.NEUTRAL -> {
                Face(eyes = EyeStyle.SQUARE, mouth = MouthStyle.STRAIGHT)
            }

            AvatarEmotion.HAPPY -> {
                Face(eyes = EyeStyle.ARCH_UP, mouth = MouthStyle.SMILE_SM)
            }

            AvatarEmotion.EXCITED -> {
                Face(
                    eyes = EyeStyle.WIDE_O,
                    mouth = MouthStyle.SMILE_LG,
                    extras = listOf(Extra.SPARKLE_TL, Extra.SPARKLE_TR, Extra.SPARKLE_BL, Extra.SPARKLE_BR),
                )
            }

            AvatarEmotion.SAD -> {
                Face(
                    eyes = EyeStyle.ARCH_DOWN,
                    mouth = MouthStyle.FROWN,
                    brow = BrowStyle.SAD,
                    extras = listOf(Extra.TEAR_LEFT),
                )
            }

            AvatarEmotion.ANGRY -> {
                Face(eyes = EyeStyle.SQUARE, mouth = MouthStyle.STRAIGHT, brow = BrowStyle.ANGRY)
            }

            AvatarEmotion.SURPRISED -> {
                Face(eyes = EyeStyle.WIDE_O, mouth = MouthStyle.O, brow = BrowStyle.CURIOUS)
            }

            AvatarEmotion.SLEEPY -> {
                Face(eyes = EyeStyle.SLEEPY_DASH, mouth = MouthStyle.TILDE)
            }

            AvatarEmotion.THINKING -> {
                Face(
                    eyes = EyeStyle.SQUARE,
                    mouth = MouthStyle.DOTS_THINKING,
                    brow = BrowStyle.CURIOUS,
                )
            }

            AvatarEmotion.LOVE -> {
                Face(
                    eyes = EyeStyle.HEART,
                    mouth = MouthStyle.SMILE_LG,
                    extras = listOf(Extra.HEART_CHEEK_LEFT, Extra.HEART_CHEEK_RIGHT),
                )
            }
        }
    return if (blink) base.copy(eyes = EyeStyle.CLOSED) else base
}

fun Face.rasterize(): BooleanArray {
    val bits = BooleanArray(GRID_SIZE)
    drawHead(bits)
    drawBrow(bits, brow)
    drawEyePair(bits, eyes)
    drawMouth(bits, mouth)
    extras.forEach { drawExtra(bits, it) }
    return bits
}

// ── primitives ────────────────────────────────────────────────────────────

private fun setPixel(
    bits: BooleanArray,
    col: Int,
    row: Int,
) {
    if (col in 0 until DOT_GRID && row in 0 until DOT_GRID) {
        bits[col + row * DOT_GRID] = true
    }
}

private fun fillRect(
    bits: BooleanArray,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
) {
    for (r in y until y + h) for (c in x until x + w) setPixel(bits, c, r)
}

private fun strokeRect(
    bits: BooleanArray,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
) {
    for (c in x until x + w) {
        setPixel(bits, c, y)
        setPixel(bits, c, y + h - 1)
    }
    for (r in y until y + h) {
        setPixel(bits, x, r)
        setPixel(bits, x + w - 1, r)
    }
}

private fun fillCircle(
    bits: BooleanArray,
    cx: Int,
    cy: Int,
    radius: Int,
) {
    val r2 = radius * radius
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            if (dx * dx + dy * dy <= r2) setPixel(bits, cx + dx, cy + dy)
        }
    }
}

private fun strokeCircle(
    bits: BooleanArray,
    cx: Int,
    cy: Int,
    radius: Int,
) {
    val outer = radius * radius
    val inner = (radius - 1) * (radius - 1)
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            val d = dx * dx + dy * dy
            if (d <= outer && d >= inner) setPixel(bits, cx + dx, cy + dy)
        }
    }
}

// Rounded-rect outline using a pair of inset tests.
private fun inRoundedRect(
    x: Int,
    y: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    radius: Int,
): Boolean {
    if (x < left || x > right || y < top || y > bottom) return false
    val cornerX =
        when {
            x < left + radius -> left + radius
            x > right - radius -> right - radius
            else -> x
        }
    val cornerY =
        when {
            y < top + radius -> top + radius
            y > bottom - radius -> bottom - radius
            else -> y
        }
    val dx = x - cornerX
    val dy = y - cornerY
    return dx * dx + dy * dy <= radius * radius
}

// ── head ─────────────────────────────────────────────────────────────────

private fun drawHead(bits: BooleanArray) {
    val left = 4
    val top = 4
    val right = 59
    val bottom = 59
    val radius = 10
    for (r in top..bottom) {
        for (c in left..right) {
            val inOuter = inRoundedRect(c, r, left, top, right, bottom, radius)
            val inInner = inRoundedRect(c, r, left + 2, top + 2, right - 2, bottom - 2, radius - 2)
            if (inOuter && !inInner) setPixel(bits, c, r)
        }
    }
}

// ── eyes ─────────────────────────────────────────────────────────────────

// Eye centres: left=(20,26), right=(43,26). Zone = 12h × 14w per eye.
private const val L_EYE_CX = 20
private const val R_EYE_CX = 43
private const val EYE_CY = 26

private fun drawEyePair(
    bits: BooleanArray,
    style: EyeStyle,
) {
    drawEye(bits, L_EYE_CX, EYE_CY, style, leftSide = true)
    drawEye(bits, R_EYE_CX, EYE_CY, style, leftSide = false)
}

private fun drawEye(
    bits: BooleanArray,
    cx: Int,
    cy: Int,
    style: EyeStyle,
    leftSide: Boolean,
) {
    when (style) {
        EyeStyle.SQUARE -> {
            strokeRect(bits, cx - 3, cy - 3, 6, 6)
        }

        EyeStyle.WIDE_O -> {
            strokeCircle(bits, cx, cy, 4)
        }

        EyeStyle.ARCH_UP -> {
            // Arc opening downward (smiling eyes) ^^
            for (dx in -4..4) {
                val y = cy - ((4 - dx * dx / 4.coerceAtLeast(1)).let { if (it < -1) -1 else it })
                // use parabola: y = cy - round(3 - dx^2 * 0.22)
                val dy = (3 - dx * dx * 0.22).toInt().coerceAtLeast(-1)
                setPixel(bits, cx + dx, cy - dy)
                setPixel(bits, cx + dx, cy - dy - 1)
            }
        }

        EyeStyle.ARCH_DOWN -> {
            // Arc opening upward (sad eyes) vv
            for (dx in -4..4) {
                val dy = (3 - dx * dx * 0.22).toInt().coerceAtLeast(-1)
                setPixel(bits, cx + dx, cy + dy)
                setPixel(bits, cx + dx, cy + dy + 1)
            }
        }

        EyeStyle.CLOSED -> {
            // Horizontal line, straight
            for (dx in -4..4) {
                setPixel(bits, cx + dx, cy + 1)
                setPixel(bits, cx + dx, cy + 2)
            }
        }

        EyeStyle.SLEEPY_DASH -> {
            // Two short dashes stacked
            for (dx in -3..3) {
                setPixel(bits, cx + dx, cy)
                setPixel(bits, cx + dx, cy + 1)
            }
        }

        EyeStyle.HEART -> {
            // Little heart sprite 7w × 6h
            val art =
                arrayOf(
                    ".##.##.",
                    "#######",
                    "#######",
                    ".#####.",
                    "..###..",
                    "...#...",
                )
            stampSprite(bits, art, cx - 3, cy - 2)
        }

        EyeStyle.DOTS -> {
            fillCircle(bits, cx, cy, 1)
        }
    }
}

// ── mouth ────────────────────────────────────────────────────────────────

private const val MOUTH_CX = 32
private const val MOUTH_CY = 46

private fun drawMouth(
    bits: BooleanArray,
    style: MouthStyle,
) {
    when (style) {
        MouthStyle.STRAIGHT -> {
            for (dx in -6..6) {
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY)
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY + 1)
            }
        }

        MouthStyle.SMILE_SM -> {
            for (dx in -6..6) {
                val dy = (dx * dx * 0.09).toInt()
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY + 2 - dy)
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY + 3 - dy)
            }
        }

        MouthStyle.SMILE_LG -> {
            for (dx in -8..8) {
                val dy = (dx * dx * 0.06).toInt()
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY + 4 - dy)
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY + 5 - dy)
            }
            // Fill the smile so it looks open-mouth happy
            for (dx in -6..6) {
                val topY = MOUTH_CY + 4 - (dx * dx * 0.06).toInt()
                val bottomY = MOUTH_CY + 5 - (dx * dx * 0.06).toInt()
                if (dx in -5..5) setPixel(bits, MOUTH_CX + dx, topY - 1)
            }
        }

        MouthStyle.FROWN -> {
            for (dx in -6..6) {
                val dy = (dx * dx * 0.09).toInt()
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY - 2 + dy)
                setPixel(bits, MOUTH_CX + dx, MOUTH_CY - 1 + dy)
            }
        }

        MouthStyle.O -> {
            strokeCircle(bits, MOUTH_CX, MOUTH_CY + 1, 3)
        }

        MouthStyle.TILDE -> {
            // Sleepy squiggle ~
            setPixel(bits, MOUTH_CX - 5, MOUTH_CY + 1)
            setPixel(bits, MOUTH_CX - 4, MOUTH_CY)
            setPixel(bits, MOUTH_CX - 3, MOUTH_CY - 1)
            setPixel(bits, MOUTH_CX - 2, MOUTH_CY)
            setPixel(bits, MOUTH_CX - 1, MOUTH_CY + 1)
            setPixel(bits, MOUTH_CX, MOUTH_CY + 1)
            setPixel(bits, MOUTH_CX + 1, MOUTH_CY)
            setPixel(bits, MOUTH_CX + 2, MOUTH_CY - 1)
            setPixel(bits, MOUTH_CX + 3, MOUTH_CY)
            setPixel(bits, MOUTH_CX + 4, MOUTH_CY + 1)
        }

        MouthStyle.DOTS_THINKING -> {
            fillCircle(bits, MOUTH_CX - 6, MOUTH_CY + 1, 1)
            fillCircle(bits, MOUTH_CX, MOUTH_CY + 1, 1)
            fillCircle(bits, MOUTH_CX + 6, MOUTH_CY + 1, 1)
        }
    }
}

// ── brow ─────────────────────────────────────────────────────────────────

private fun drawBrow(
    bits: BooleanArray,
    style: BrowStyle,
) {
    val browY = 17
    when (style) {
        BrowStyle.NONE -> {
            Unit
        }

        BrowStyle.ANGRY -> {
            // Down-and-in diagonals: \   /
            for (i in 0..5) {
                setPixel(bits, L_EYE_CX - 4 + i, browY + i / 2)
                setPixel(bits, L_EYE_CX - 4 + i, browY + i / 2 + 1)
                setPixel(bits, R_EYE_CX + 4 - i, browY + i / 2)
                setPixel(bits, R_EYE_CX + 4 - i, browY + i / 2 + 1)
            }
        }

        BrowStyle.SAD -> {
            // Up-and-in diagonals (inverted angry)
            for (i in 0..5) {
                setPixel(bits, L_EYE_CX - 4 + i, browY + 2 - i / 2)
                setPixel(bits, L_EYE_CX - 4 + i, browY + 3 - i / 2)
                setPixel(bits, R_EYE_CX + 4 - i, browY + 2 - i / 2)
                setPixel(bits, R_EYE_CX + 4 - i, browY + 3 - i / 2)
            }
        }

        BrowStyle.CURIOUS -> {
            // One eyebrow raised (left-side only)
            for (dx in -4..4) {
                setPixel(bits, L_EYE_CX + dx, browY)
                setPixel(bits, L_EYE_CX + dx, browY + 1)
            }
        }
    }
}

// ── extras ───────────────────────────────────────────────────────────────

private fun drawExtra(
    bits: BooleanArray,
    extra: Extra,
) {
    when (extra) {
        Extra.TEAR_LEFT -> {
            // Teardrop under left eye
            val tx = L_EYE_CX - 4
            val ty = 34
            setPixel(bits, tx, ty)
            fillCircle(bits, tx, ty + 1, 1)
            fillCircle(bits, tx, ty + 3, 1)
        }

        Extra.SPARKLE_TL -> {
            sparkle(bits, 10, 10)
        }

        Extra.SPARKLE_TR -> {
            sparkle(bits, 53, 10)
        }

        Extra.SPARKLE_BL -> {
            sparkle(bits, 10, 53)
        }

        Extra.SPARKLE_BR -> {
            sparkle(bits, 53, 53)
        }

        Extra.HEART_CHEEK_LEFT -> {
            heartSprite(bits, 11, 38)
        }

        Extra.HEART_CHEEK_RIGHT -> {
            heartSprite(bits, 52, 38)
        }
    }
}

private fun sparkle(
    bits: BooleanArray,
    cx: Int,
    cy: Int,
) {
    // 4-point star
    for (d in -2..2) {
        setPixel(bits, cx + d, cy)
        setPixel(bits, cx, cy + d)
    }
    setPixel(bits, cx, cy) // centre
}

private fun heartSprite(
    bits: BooleanArray,
    cx: Int,
    cy: Int,
) {
    val art =
        arrayOf(
            ".##.##.",
            "#######",
            "#######",
            ".#####.",
            "..###..",
            "...#...",
        )
    stampSprite(bits, art, cx - 3, cy - 2)
}

private fun stampSprite(
    bits: BooleanArray,
    art: Array<String>,
    originX: Int,
    originY: Int,
) {
    for (r in art.indices) {
        val row = art[r]
        for (c in row.indices) {
            if (row[c] == '#') setPixel(bits, originX + c, originY + r)
        }
    }
}
