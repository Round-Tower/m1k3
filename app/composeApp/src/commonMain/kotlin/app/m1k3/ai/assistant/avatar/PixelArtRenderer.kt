package app.m1k3.ai.assistant.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 間 AI Pixel Art Renderer
 *
 * Renders the pixel pet as a true pixel art grid with **adaptive resolution**.
 * Grid size scales based on container size for maximum detail.
 *
 * **Design Philosophy:**
 * - Adaptive resolution: Small container = 16x16, Large container = 64x64+
 * - Clear pixel boundaries (visible grid squares)
 * - Evolution affects sprite complexity
 * - Emotion affects sprite pattern
 * - Activity affects animation frame
 *
 * **Resolution Tiers:**
 * - Tiny (< 150dp): 16x16 pixels
 * - Small (150-300dp): 32x32 pixels
 * - Medium (300-500dp): 48x48 pixels
 * - Large (> 500dp): 64x64 pixels
 */

/**
 * Calculate optimal grid size based on container size
 */
fun calculateGridSize(containerSizeDp: Float): Int {
    return when {
        containerSizeDp < 150f -> 16  // Tiny (mobile compact)
        containerSizeDp < 300f -> 32  // Small (mobile standard)
        containerSizeDp < 500f -> 48  // Medium (tablet/large phone)
        else -> 64                     // Large (tablet landscape/desktop)
    }
}

/**
 * Pixel art sprite data (16x16 grid)
 *
 * Each sprite is represented as a list of 16 strings (rows),
 * where each character represents a pixel color:
 * - '.' = transparent
 * - '#' = primary color (body)
 * - '@' = secondary color (details)
 * - 'O' = eyes
 * - '*' = special effects
 */
object PixelArtSprites {

    // === BASIC Stage Sprites (Simple shapes) ===

    val BASIC_HAPPY = listOf(
        "................",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "...##@@@@@##....",
        "....########....",
        ".....######.....",
        "......####......",
        "................",
        "................",
        "................",
        "................",
        "................"
    )

    val BASIC_SAD = listOf(
        "................",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "....@@@@@@......",
        "....########....",
        ".....######.....",
        "......####......",
        "................",
        "................",
        "................",
        "................",
        "................"
    )

    val BASIC_NEUTRAL = listOf(
        "................",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "...##@@@@@@##...",
        "....########....",
        ".....######.....",
        "......####......",
        "................",
        "................",
        "................",
        "................",
        "................"
    )

    // === GROWING Stage Sprites (More details) ===

    val GROWING_HAPPY = listOf(
        "................",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "..############..",
        "..@@########@@..",
        "..@@##@@@@##@@..",
        "..############..",
        "...##########...",
        "....########....",
        ".....######.....",
        "......@@@@......",
        "................",
        "................",
        "................"
    )

    val GROWING_EXCITED = listOf(
        "......****......",
        ".....######.....",
        ".*..########..*.",
        "...##O####O##...",
        "..############..",
        "..@@########@@..",
        "..@@##OOOO##@@..",
        "..############..",
        ".*###########.*.",
        "....########....",
        ".....######.....",
        "......@@@@......",
        "......*...*.....",
        "................",
        "................",
        "................"
    )

    // === ADVANCED Stage Sprites (Complex) ===

    val ADVANCED_HAPPY = listOf(
        "......****......",
        "....@@@@@@@@....",
        "...##########...",
        "..##O######O##..",
        ".##############.",
        ".@@##########@@.",
        ".@@##@@@@@@##@@.",
        ".@@############.",
        ".##############.",
        "..############..",
        "...##@@@@@@##...",
        "....@@....@@....",
        "....@@....@@....",
        ".....*.....*..",
        "................",
        "................"
    )

    val ADVANCED_THINKING = listOf(
        ".....**.........",
        "....**@@@@@@....",
        "...**########...",
        "..##O######O##..",
        ".##############.",
        ".@@##########@@.",
        ".@@##......##@@.",
        ".@@############.",
        ".##############.",
        "..############..",
        "...##@@@@@@##...",
        "....@@....@@....",
        "....@@....@@....",
        "................",
        "................",
        "................"
    )

    // === LEGENDARY Stage Sprites (Very complex) ===

    val LEGENDARY_HAPPY = listOf(
        ".**@@@@@@@@@@**.",
        ".*@@@@@@@@@@@@*.",
        "..@@##########@@",
        ".@@##O####O##@@.",
        ".@@############.",
        "@@@##########@@@",
        "@@@##@@@@@@##@@@",
        "@@@############@",
        ".@@############.",
        "..@@########@@..",
        "...@@@@@@@@@@...",
        "....@@....@@....",
        "....**....**....",
        "....**..**......",
        "................",
        "................"
    )

    // === ACTIVITY-BASED Sprites (for AI state animations) ===

    val LISTENING_IDLE = listOf(
        "................",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "...*########*...",  // Antenna/ears lit up
        "....########....",
        ".....######.....",
        "......####......",
        "................",
        "................",
        "................",
        "................",
        "................"
    )

    val THINKING_PROCESS = listOf(
        "......****......",  // Thinking sparkles
        "......####......",
        "..*..######..*.",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "...##......##...",  // Mouth thinking
        "....########....",
        ".....######.....",
        "......####......",
        ".......*........",  // Idea spark
        "................",
        "................",
        "................",
        "................"
    )

    val GENERATING_ACTIVE = listOf(
        "....**..**.....",
        "..**########**..",
        ".*############*.",
        "..##########....",
        ".*##########*...",
        "*.*##O####O##*.*",  // Energy pulsing
        ".*##########*...",
        ".*##@@@@@@##*...",
        "..##########....",
        ".*############*.",
        "..**########**..",
        "....**..**.....",
        "......*.*.......",
        "................",
        "................",
        "................"
    )

    val SPEAKING_MOUTH = listOf(
        "................",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "...##O@@@@O##...",  // Open mouth
        "....########....",
        ".....######.....",
        "......####......",
        "................",
        "................",
        "................",
        "................",
        "................"
    )

    val ERROR_GLITCH = listOf(
        "..##............",  // Glitchy/distorted
        "......##........",
        ".....####.##....",
        "....########....",
        "...##.###.###...",
        "...##O####O##...",
        "...##########...",
        "....@@....@@....",  // Sad/error mouth
        "....########....",
        ".....##..##.....",
        "......####......",
        "..........##....",
        "................",
        "................",
        "................",
        "................"
    )

    val IDLE_BREATHING = listOf(
        "................",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##O####O##...",
        "...##########...",
        "...##@@@@@@##...",  // Neutral mouth
        "....########....",
        ".....######.....",
        "......####......",
        "................",
        "................",
        "................",
        "................",
        "................"
    )
}

/**
 * Scale sprite to target resolution using nearest-neighbor upscaling
 */
fun scaleSprite(sprite: List<String>, targetSize: Int): List<String> {
    val sourceSize = sprite.size
    if (sourceSize == targetSize) return sprite

    val scaleFactor = targetSize.toFloat() / sourceSize.toFloat()
    val scaled = mutableListOf<String>()

    for (y in 0 until targetSize) {
        val sourceY = (y / scaleFactor).toInt().coerceIn(0, sourceSize - 1)
        val sourceRow = sprite[sourceY]
        val scaledRow = StringBuilder()

        for (x in 0 until targetSize) {
            val sourceX = (x / scaleFactor).toInt().coerceIn(0, sourceRow.length - 1)
            scaledRow.append(sourceRow[sourceX])
        }

        scaled.add(scaledRow.toString())
    }

    return scaled
}

/**
 * Get sprite based on pet state (base 16x16 resolution)
 */
fun getPixelArtSprite(petState: PixelPetState, avatarState: AvatarState): List<String> {
    // Priority 1: Activity-based sprites (overrides emotion when active)
    return when (avatarState.activity) {
        AvatarActivity.LISTENING -> PixelArtSprites.LISTENING_IDLE
        AvatarActivity.THINKING -> PixelArtSprites.THINKING_PROCESS
        AvatarActivity.GENERATING -> PixelArtSprites.GENERATING_ACTIVE
        AvatarActivity.SPEAKING -> PixelArtSprites.SPEAKING_MOUTH
        AvatarActivity.ERROR -> PixelArtSprites.ERROR_GLITCH

        // Priority 2: Emotion-based sprites when IDLE
        AvatarActivity.IDLE -> when (petState.evolutionStage) {
            EvolutionStage.BASIC -> when (avatarState.emotion) {
                AvatarEmotion.HAPPY, AvatarEmotion.EXCITED, AvatarEmotion.LOVE ->
                    PixelArtSprites.BASIC_HAPPY
                AvatarEmotion.SAD, AvatarEmotion.SLEEPY ->
                    PixelArtSprites.BASIC_SAD
                else ->
                    PixelArtSprites.BASIC_NEUTRAL
            }

            EvolutionStage.INTERMEDIATE -> when (avatarState.emotion) {
                AvatarEmotion.EXCITED ->
                    PixelArtSprites.GROWING_EXCITED
                else ->
                    PixelArtSprites.GROWING_HAPPY
            }

            EvolutionStage.ADVANCED -> when (avatarState.emotion) {
                AvatarEmotion.THINKING ->
                    PixelArtSprites.ADVANCED_THINKING
                else ->
                    PixelArtSprites.ADVANCED_HAPPY
            }

            EvolutionStage.EXPERT ->
                PixelArtSprites.ADVANCED_HAPPY // Reuse advanced sprites

            EvolutionStage.LEGENDARY ->
                PixelArtSprites.LEGENDARY_HAPPY
        }
    }
}

/**
 * Get color for pixel character
 */
fun getPixelColor(char: Char, visualTheme: VisualTheme, emotion: AvatarEmotion): Color {
    return when (char) {
        '#' -> when (visualTheme) {
            VisualTheme.BASIC -> Color(0xFF808080) // Gray
            VisualTheme.SLEEK -> Color(0xFFC0C0C0) // Silver
            VisualTheme.CRYSTALLINE -> Color(0xFF4169E1) // Royal Blue
            VisualTheme.ENERGY -> Color(0xFFE25303) // M1K3 Orange
            VisualTheme.LEGENDARY -> Color(0xFFFFD700) // Gold
        }
        '@' -> when (visualTheme) {
            VisualTheme.BASIC -> Color(0xFFA0A0A0) // Light gray
            VisualTheme.SLEEK -> Color(0xFFE0E0E0) // Light silver
            VisualTheme.CRYSTALLINE -> Color(0xFF6A89E1) // Light blue
            VisualTheme.ENERGY -> Color(0xFFFF8C42) // Light orange
            VisualTheme.LEGENDARY -> Color(0xFFFFE54F) // Light gold
        }
        'O' -> when (emotion) {
            AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> Color(0xFF4CAF50) // Green eyes
            AvatarEmotion.SAD, AvatarEmotion.SLEEPY -> Color(0xFF90CAF9) // Blue eyes
            AvatarEmotion.ANGRY -> Color(0xFFEF5350) // Red eyes
            AvatarEmotion.LOVE -> Color(0xFFF48FB1) // Pink eyes
            else -> Color.White // Default white eyes
        }
        '*' -> Color(0xFFFFD54F) // Sparkle/effect color
        else -> Color.Transparent
    }
}

/**
 * Draw pixel art sprite on canvas with adaptive resolution
 * Includes 1px padding and subtle rounded corners (15% radius)
 */
fun DrawScope.drawPixelArtSprite(
    sprite: List<String>,
    petState: PixelPetState,
    emotion: AvatarEmotion,
    centerX: Float,
    centerY: Float,
    pixelSize: Float,
    gridSize: Int = 16,
    useRoundedPixels: Boolean = true
) {
    val totalWidth = gridSize * pixelSize
    val totalHeight = gridSize * pixelSize
    val startX = centerX - totalWidth / 2
    val startY = centerY - totalHeight / 2

    // 1px padding between pixels
    val padding = 1f
    val adjustedPixelSize = pixelSize - padding

    // Subtle corner radius (15% of pixel size)
    val cornerRadius = if (useRoundedPixels) adjustedPixelSize * 0.15f else 0f

    // Draw each pixel
    sprite.forEachIndexed { rowIndex, row ->
        row.forEachIndexed { colIndex, char ->
            if (char != '.') {
                val pixelColor = getPixelColor(char, petState.visualTheme, emotion)

                if (useRoundedPixels) {
                    drawRoundRect(
                        color = pixelColor,
                        topLeft = Offset(
                            x = startX + colIndex * pixelSize + padding / 2,
                            y = startY + rowIndex * pixelSize + padding / 2
                        ),
                        size = Size(adjustedPixelSize, adjustedPixelSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                } else {
                    // Fallback to square pixels
                    drawRect(
                        color = pixelColor,
                        topLeft = Offset(
                            x = startX + colIndex * pixelSize + padding / 2,
                            y = startY + rowIndex * pixelSize + padding / 2
                        ),
                        size = Size(adjustedPixelSize, adjustedPixelSize)
                    )
                }
            }
        }
    }
}

/**
 * Draw pixel art pet with adaptive resolution
 */
fun DrawScope.drawPixelArtPet(
    petState: PixelPetState,
    avatarState: AvatarState,
    showEnvironment: Boolean = true,
    showPixelGrid: Boolean = false,
    showResolutionDebug: Boolean = false,
    useRoundedPixels: Boolean = true,
    isDarkMode: Boolean = true
) {
    // 1. Draw environment background (theme-aware)
    if (showEnvironment) {
        drawEnvironmentBackground(petState.environment, petState.evolutionStage, isDarkMode)
    }

    // 2. Calculate optimal grid size based on container size
    val availableSize = minOf(size.width, size.height)
    val containerSizeDp = availableSize / 1f // Approximate dp conversion
    val gridSize = calculateGridSize(containerSizeDp)

    // 3. Calculate pixel size to fit container
    val pixelSize = availableSize / (gridSize + 4f) // Grid + 4 pixel padding

    // 4. Get base sprite and scale to target resolution
    val baseSprite = getPixelArtSprite(petState, avatarState)
    val scaledSprite = scaleSprite(baseSprite, gridSize)

    // 5. Draw pixel art sprite with rounded pixels
    drawPixelArtSprite(
        sprite = scaledSprite,
        petState = petState,
        emotion = avatarState.emotion,
        centerX = size.width / 2,
        centerY = size.height / 2,
        pixelSize = pixelSize,
        gridSize = gridSize,
        useRoundedPixels = useRoundedPixels
    )

    // 6. Optional: Draw pixel grid lines for detail
    if (showPixelGrid) {
        drawPixelGrid(
            centerX = size.width / 2,
            centerY = size.height / 2,
            pixelSize = pixelSize,
            gridSize = gridSize
        )
    }

    // 7. Optional: Show resolution debug info
    if (showResolutionDebug) {
        drawResolutionDebugInfo(
            gridSize = gridSize,
            pixelSize = pixelSize,
            containerSize = availableSize
        )
    }
}

/**
 * Draw pixel grid lines for extra detail/clarity
 */
fun DrawScope.drawPixelGrid(
    centerX: Float,
    centerY: Float,
    pixelSize: Float,
    gridSize: Int
) {
    val totalWidth = gridSize * pixelSize
    val totalHeight = gridSize * pixelSize
    val startX = centerX - totalWidth / 2
    val startY = centerY - totalHeight / 2

    val gridColor = Color(0x20FFFFFF) // Subtle white grid

    // Draw vertical lines
    for (i in 0..gridSize) {
        drawLine(
            color = gridColor,
            start = Offset(startX + i * pixelSize, startY),
            end = Offset(startX + i * pixelSize, startY + totalHeight),
            strokeWidth = 0.5f
        )
    }

    // Draw horizontal lines
    for (i in 0..gridSize) {
        drawLine(
            color = gridColor,
            start = Offset(startX, startY + i * pixelSize),
            end = Offset(startX + totalWidth, startY + i * pixelSize),
            strokeWidth = 0.5f
        )
    }
}

/**
 * Draw resolution debug info in corner
 */
fun DrawScope.drawResolutionDebugInfo(
    gridSize: Int,
    pixelSize: Float,
    containerSize: Float
) {
    // Draw semi-transparent background
    drawRect(
        color = Color(0xCC000000),
        topLeft = Offset(8f, 8f),
        size = Size(180f, 60f)
    )

    // Note: Text rendering would require actual Text composable
    // This is just a visual marker showing resolution tier
    val tierColor = when (gridSize) {
        16 -> Color(0xFFFF5252) // Red - Tiny
        32 -> Color(0xFFFFA726) // Orange - Small
        48 -> Color(0xFFFFEB3B) // Yellow - Medium
        64 -> Color(0xFF4CAF50) // Green - Large
        else -> Color.White
    }

    drawCircle(
        color = tierColor,
        radius = 6f,
        center = Offset(20f, 20f)
    )
}

/**
 * Draw environment background (theme-aware)
 * @param isDarkMode If true, uses dark theme colors; if false, uses light theme colors
 */
private fun DrawScope.drawEnvironmentBackground(
    environment: Environment,
    stage: EvolutionStage,
    isDarkMode: Boolean = true
) {
    when (environment) {
        Environment.VOID -> {
            // Simple gradient (dark or light)
            drawRect(
                color = if (isDarkMode) Color(0xFF0A0A0A) else Color(0xFFF0F0F0),
                size = size
            )
        }
        Environment.OFFICE -> {
            // Modern office: floor + window hints
            if (isDarkMode) {
                drawRect(Color(0xFF1A1A1A), size = size) // Dark floor
                drawRect(Color(0xFF2A2A2A), Offset(0f, 0f), Size(size.width, size.height * 0.3f)) // Window hint
            } else {
                drawRect(Color(0xFFE8E8E8), size = size) // Light floor
                drawRect(Color(0xFFD0D0D0), Offset(0f, 0f), Size(size.width, size.height * 0.3f)) // Window hint
            }
        }
        Environment.GARDEN -> {
            // Green tones with grass (theme-aware)
            if (isDarkMode) {
                drawRect(Color(0xFF0D1F0D), size = size) // Dark green
                drawRect(Color(0xFF1A3A1A), Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f))
            } else {
                drawRect(Color(0xFFC8E6C9), size = size) // Light green
                drawRect(Color(0xFFA5D6A7), Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f)) // Grass
            }
        }
        Environment.LAB -> {
            // Tech lab: blue tones with grid
            if (isDarkMode) {
                drawRect(Color(0xFF0A0F1F), size = size)
                // Grid lines
                val gridSpacing = 40f
                for (i in 0..((size.width / gridSpacing).toInt())) {
                    drawLine(
                        Color(0xFF1A2F4F),
                        Offset(i * gridSpacing, 0f),
                        Offset(i * gridSpacing, size.height),
                        strokeWidth = 1f
                    )
                }
            } else {
                drawRect(Color(0xFFE3F2FD), size = size) // Light blue background
                // Grid lines in light mode
                val gridSpacing = 40f
                for (i in 0..((size.width / gridSpacing).toInt())) {
                    drawLine(
                        Color(0xFFBBDEFB),
                        Offset(i * gridSpacing, 0f),
                        Offset(i * gridSpacing, size.height),
                        strokeWidth = 1f
                    )
                }
            }
        }
        Environment.SPACE_STATION -> {
            // Space: deep background with stars (always dark for space aesthetic)
            drawRect(Color(0xFF000814), size = size) // Even darker for space
            // Draw random stars (always bright)
            repeat(30) {
                val x = (0..size.width.toInt()).random().toFloat()
                val y = (0..size.height.toInt()).random().toFloat()
                drawCircle(Color(0xFFFAFAFA), radius = 1f, center = Offset(x, y))
            }
        }
    }
}
