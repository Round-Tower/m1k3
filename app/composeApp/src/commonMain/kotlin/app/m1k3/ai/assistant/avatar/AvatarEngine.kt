package app.m1k3.ai.assistant.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * 間 AI Avatar Rendering Engine
 *
 * Canvas-based robot avatar renderer with emotion-aware features.
 * Draws minimalist robot using geometric primitives.
 */

object AvatarEngine {
    /**
     * Render complete robot avatar
     *
     * @param state Current avatar state (emotion, activity, intensity)
     * @param geometry Visual parameters (sizes, positions)
     * @param animation Animation parameters (bounce, rotation)
     */
    fun DrawScope.drawRobotAvatar(
        state: AvatarState,
        geometry: RobotGeometry = RobotGeometry(),
        animation: AvatarAnimation = AvatarAnimation()
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Apply animation offsets - always animate, reduce intensity when idle
        val bounceIntensity = if (state.activity.isActive) 1f else 0.3f
        val bounceOffset = sin(state.animationProgress * Math.PI * 2).toFloat() *
            animation.bounceHeight * bounceIntensity

        val emotionColor = state.displayColor

        // Draw antenna with pulsing glow
        drawAntenna(
            center = Offset(centerX, centerY - geometry.headSize / 2),
            geometry = geometry,
            color = emotionColor,
            isActive = state.isAnimating,
            animationProgress = state.animationProgress
        )

        // Draw head
        drawHead(
            center = Offset(centerX, centerY + bounceOffset),
            geometry = geometry,
            color = emotionColor
        )

        // Blinking animation - blink every ~5 seconds
        val blinkCycle = 5f  // Seconds between blinks (at 2s animation loop)
        val blinkDuration = 0.03f  // 3% of cycle (quick blink)
        val cyclePosition = state.animationProgress % (blinkCycle / 2f)
        val shouldBlink = cyclePosition < blinkDuration

        // Draw eyes based on emotion (use SLEEPY for closed eyes when blinking)
        drawEyes(
            center = Offset(centerX, centerY + bounceOffset + geometry.eyeVerticalOffset),
            emotion = if (shouldBlink) AvatarEmotion.SLEEPY else state.emotion,
            geometry = geometry,
            color = emotionColor
        )

        // Draw mouth based on emotion
        drawMouth(
            center = Offset(centerX, centerY + bounceOffset + geometry.mouthVerticalOffset),
            emotion = state.emotion,
            geometry = geometry,
            color = emotionColor,
            intensity = state.intensity
        )
    }

    /**
     * Draw robot head (rounded rectangle)
     */
    private fun DrawScope.drawHead(
        center: Offset,
        geometry: RobotGeometry,
        color: Color
    ) {
        val headRect = RoundRect(
            rect = Rect(
                left = center.x - geometry.headSize / 2,
                top = center.y - geometry.headSize / 2,
                right = center.x + geometry.headSize / 2,
                bottom = center.y + geometry.headSize / 2
            ),
            cornerRadius = CornerRadius(geometry.headCornerRadius)
        )

        // Fill
        drawRoundRect(
            color = geometry.backgroundColor,
            topLeft = Offset(headRect.left, headRect.top),
            size = Size(headRect.width, headRect.height),
            cornerRadius = CornerRadius(geometry.headCornerRadius)
        )

        // Emotion color tint overlay
        drawRoundRect(
            color = color.copy(alpha = 0.1f),
            topLeft = Offset(headRect.left, headRect.top),
            size = Size(headRect.width, headRect.height),
            cornerRadius = CornerRadius(geometry.headCornerRadius)
        )

        // Outline
        drawRoundRect(
            color = geometry.outlineColor,
            topLeft = Offset(headRect.left, headRect.top),
            size = Size(headRect.width, headRect.height),
            cornerRadius = CornerRadius(geometry.headCornerRadius),
            style = Stroke(width = geometry.outlineWidth)
        )
    }

    /**
     * Draw antenna with glowing bulb (pulsing animation)
     */
    private fun DrawScope.drawAntenna(
        center: Offset,
        geometry: RobotGeometry,
        color: Color,
        isActive: Boolean,
        animationProgress: Float = 0f
    ) {
        // Antenna stem
        drawLine(
            color = geometry.outlineColor,
            start = center,
            end = Offset(center.x, center.y - geometry.antennaHeight),
            strokeWidth = geometry.antennaWidth
        )

        // Antenna bulb
        val bulbAlpha = if (isActive) 1.0f else 0.6f
        drawCircle(
            color = color.copy(alpha = bulbAlpha),
            radius = geometry.antennaBulbRadius,
            center = Offset(center.x, center.y - geometry.antennaHeight)
        )

        // Bulb outline
        drawCircle(
            color = geometry.outlineColor,
            radius = geometry.antennaBulbRadius,
            center = Offset(center.x, center.y - geometry.antennaHeight),
            style = Stroke(width = geometry.outlineWidth)
        )

        // Pulsing glow effect (always visible, more intense when active)
        val glowIntensity = sin(animationProgress * Math.PI * 2).toFloat() * 0.5f + 0.5f
        val baseAlpha = if (isActive) 0.4f else 0.2f
        drawCircle(
            color = color.copy(alpha = baseAlpha * glowIntensity),
            radius = geometry.antennaBulbRadius * (1.5f + glowIntensity * 0.3f),
            center = Offset(center.x, center.y - geometry.antennaHeight)
        )
    }

    /**
     * Draw eyes based on emotion
     */
    private fun DrawScope.drawEyes(
        center: Offset,
        emotion: AvatarEmotion,
        geometry: RobotGeometry,
        color: Color
    ) {
        val leftEyeX = center.x - geometry.eyeSpacing / 2
        val rightEyeX = center.x + geometry.eyeSpacing / 2

        when (emotion) {
            AvatarEmotion.HAPPY -> {
                // Curved happy eyes (arcs)
                drawHappyEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.SAD -> {
                // Downward curved sad eyes
                drawSadEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.ANGRY -> {
                // Angled angry eyes
                drawAngryEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.SURPRISED -> {
                // Wide circular eyes
                drawSurprisedEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.LOVE -> {
                // Heart-shaped eyes
                drawLoveEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.THINKING -> {
                // Squinted analytical eyes
                drawThinkingEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.SLEEPY -> {
                // Half-closed drowsy eyes
                drawSleepyEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.EXCITED -> {
                // Wide energetic eyes with sparkle
                drawExcitedEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
            AvatarEmotion.NEUTRAL -> {
                // Simple horizontal lines
                drawNeutralEyes(leftEyeX, rightEyeX, center.y, geometry, color)
            }
        }
    }

    private fun DrawScope.drawNeutralEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        // Left eye
        drawLine(
            color = color,
            start = Offset(leftX - geometry.eyeWidth / 2, y),
            end = Offset(leftX + geometry.eyeWidth / 2, y),
            strokeWidth = geometry.eyeHeight
        )
        // Right eye
        drawLine(
            color = color,
            start = Offset(rightX - geometry.eyeWidth / 2, y),
            end = Offset(rightX + geometry.eyeWidth / 2, y),
            strokeWidth = geometry.eyeHeight
        )
    }

    private fun DrawScope.drawHappyEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        val path = Path()
        // Left eye (curved upward)
        path.moveTo(leftX - geometry.eyeWidth / 2, y)
        path.quadraticBezierTo(
            leftX, y - 10f,
            leftX + geometry.eyeWidth / 2, y
        )
        drawPath(path, color, style = Stroke(width = geometry.eyeHeight))

        val path2 = Path()
        // Right eye (curved upward)
        path2.moveTo(rightX - geometry.eyeWidth / 2, y)
        path2.quadraticBezierTo(
            rightX, y - 10f,
            rightX + geometry.eyeWidth / 2, y
        )
        drawPath(path2, color, style = Stroke(width = geometry.eyeHeight))
    }

    private fun DrawScope.drawSadEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        val path = Path()
        // Left eye (curved downward)
        path.moveTo(leftX - geometry.eyeWidth / 2, y)
        path.quadraticBezierTo(
            leftX, y + 10f,
            leftX + geometry.eyeWidth / 2, y
        )
        drawPath(path, color, style = Stroke(width = geometry.eyeHeight))

        val path2 = Path()
        // Right eye (curved downward)
        path2.moveTo(rightX - geometry.eyeWidth / 2, y)
        path2.quadraticBezierTo(
            rightX, y + 10f,
            rightX + geometry.eyeWidth / 2, y
        )
        drawPath(path2, color, style = Stroke(width = geometry.eyeHeight))
    }

    private fun DrawScope.drawAngryEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        // Left eye (angled downward toward center)
        drawLine(
            color = color,
            start = Offset(leftX - geometry.eyeWidth / 2, y - 8f),
            end = Offset(leftX + geometry.eyeWidth / 2, y + 4f),
            strokeWidth = geometry.eyeHeight
        )
        // Right eye (angled downward toward center)
        drawLine(
            color = color,
            start = Offset(rightX - geometry.eyeWidth / 2, y + 4f),
            end = Offset(rightX + geometry.eyeWidth / 2, y - 8f),
            strokeWidth = geometry.eyeHeight
        )
    }

    private fun DrawScope.drawSurprisedEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        val radius = geometry.eyeWidth / 2
        // Left eye (circle)
        drawCircle(color = color, radius = radius, center = Offset(leftX, y))
        // Right eye (circle)
        drawCircle(color = color, radius = radius, center = Offset(rightX, y))
    }

    private fun DrawScope.drawLoveEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        val heartSize = geometry.eyeWidth / 2
        // Left heart
        drawHeart(Offset(leftX, y), heartSize, color)
        // Right heart
        drawHeart(Offset(rightX, y), heartSize, color)
    }

    private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
        val path = Path()
        path.moveTo(center.x, center.y + size / 2)
        path.cubicTo(
            center.x - size, center.y - size / 2,
            center.x - size / 2, center.y - size,
            center.x, center.y - size / 4
        )
        path.cubicTo(
            center.x + size / 2, center.y - size,
            center.x + size, center.y - size / 2,
            center.x, center.y + size / 2
        )
        drawPath(path, color)
    }

    private fun DrawScope.drawThinkingEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        // Squinted eyes (short lines)
        drawLine(
            color = color,
            start = Offset(leftX - geometry.eyeWidth / 3, y),
            end = Offset(leftX + geometry.eyeWidth / 3, y),
            strokeWidth = geometry.eyeHeight * 0.7f
        )
        drawLine(
            color = color,
            start = Offset(rightX - geometry.eyeWidth / 3, y),
            end = Offset(rightX + geometry.eyeWidth / 3, y),
            strokeWidth = geometry.eyeHeight * 0.7f
        )
    }

    private fun DrawScope.drawSleepyEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        // Half-closed (horizontal line with droop)
        drawLine(
            color = color,
            start = Offset(leftX - geometry.eyeWidth / 2, y + 3f),
            end = Offset(leftX + geometry.eyeWidth / 2, y + 3f),
            strokeWidth = geometry.eyeHeight
        )
        drawLine(
            color = color,
            start = Offset(rightX - geometry.eyeWidth / 2, y + 3f),
            end = Offset(rightX + geometry.eyeWidth / 2, y + 3f),
            strokeWidth = geometry.eyeHeight
        )
    }

    private fun DrawScope.drawExcitedEyes(
        leftX: Float, rightX: Float, y: Float,
        geometry: RobotGeometry, color: Color
    ) {
        val radius = geometry.eyeWidth / 2.2f
        // Wide eyes
        drawCircle(color = color, radius = radius, center = Offset(leftX, y))
        drawCircle(color = color, radius = radius, center = Offset(rightX, y))

        // Sparkles
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(leftX - 5f, y - radius - 8f),
            end = Offset(leftX + 5f, y - radius - 8f),
            strokeWidth = 2f
        )
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(leftX, y - radius - 13f),
            end = Offset(leftX, y - radius - 3f),
            strokeWidth = 2f
        )
    }

    /**
     * Draw mouth based on emotion
     */
    private fun DrawScope.drawMouth(
        center: Offset,
        emotion: AvatarEmotion,
        geometry: RobotGeometry,
        color: Color,
        intensity: Float
    ) {
        val mouthY = center.y
        val mouthHalfWidth = geometry.mouthWidth / 2

        when (emotion) {
            AvatarEmotion.HAPPY, AvatarEmotion.LOVE, AvatarEmotion.EXCITED -> {
                // Smile (curved upward)
                val path = Path()
                path.moveTo(center.x - mouthHalfWidth, mouthY)
                path.quadraticBezierTo(
                    center.x, mouthY + 15f * intensity,
                    center.x + mouthHalfWidth, mouthY
                )
                drawPath(path, color, style = Stroke(width = geometry.mouthHeight))
            }
            AvatarEmotion.SAD, AvatarEmotion.SLEEPY -> {
                // Frown (curved downward)
                val path = Path()
                path.moveTo(center.x - mouthHalfWidth, mouthY)
                path.quadraticBezierTo(
                    center.x, mouthY - 15f * intensity,
                    center.x + mouthHalfWidth, mouthY
                )
                drawPath(path, color, style = Stroke(width = geometry.mouthHeight))
            }
            AvatarEmotion.ANGRY -> {
                // Angry frown with sharp angle
                drawLine(
                    color = color,
                    start = Offset(center.x - mouthHalfWidth, mouthY - 8f),
                    end = Offset(center.x, mouthY),
                    strokeWidth = geometry.mouthHeight
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, mouthY),
                    end = Offset(center.x + mouthHalfWidth, mouthY - 8f),
                    strokeWidth = geometry.mouthHeight
                )
            }
            AvatarEmotion.SURPRISED -> {
                // Open circle mouth
                drawCircle(
                    color = color,
                    radius = geometry.mouthWidth / 4,
                    center = Offset(center.x, mouthY),
                    style = Stroke(width = geometry.mouthHeight)
                )
            }
            AvatarEmotion.THINKING -> {
                // Small wavy line
                val path = Path()
                path.moveTo(center.x - mouthHalfWidth / 2, mouthY)
                path.quadraticBezierTo(
                    center.x - mouthHalfWidth / 4, mouthY - 5f,
                    center.x, mouthY
                )
                path.quadraticBezierTo(
                    center.x + mouthHalfWidth / 4, mouthY + 5f,
                    center.x + mouthHalfWidth / 2, mouthY
                )
                drawPath(path, color, style = Stroke(width = geometry.mouthHeight * 0.8f))
            }
            AvatarEmotion.NEUTRAL -> {
                // Straight line
                drawLine(
                    color = color,
                    start = Offset(center.x - mouthHalfWidth, mouthY),
                    end = Offset(center.x + mouthHalfWidth, mouthY),
                    strokeWidth = geometry.mouthHeight
                )
            }
        }
    }

    // === ECO-INTEGRATED RENDERING ===

    /**
     * Draw complete pixel pet with eco integration
     *
     * Includes environment background, stat bars, and robot avatar
     */
    fun DrawScope.drawPixelPet(
        petState: PixelPetState,
        avatarState: AvatarState,
        geometry: RobotGeometry = RobotGeometry(),
        animation: AvatarAnimation = AvatarAnimation(),
        showStatBars: Boolean = true,
        showEnvironment: Boolean = true,
        showPixelGrid: Boolean = false,
        showResolutionDebug: Boolean = false,
        useRoundedPixels: Boolean = true
    ) {
        // 1. Draw pixel art pet with adaptive resolution and rounded pixels
        drawPixelArtPet(
            petState = petState,
            avatarState = avatarState,
            showEnvironment = showEnvironment,
            showPixelGrid = showPixelGrid,
            showResolutionDebug = showResolutionDebug,
            useRoundedPixels = useRoundedPixels
        )

        // 2. Draw stat bars (bottom)
        if (showStatBars) {
            drawStatBars(petState)
        }

        // 3. Draw evolution aura (if advanced stage)
        if (petState.evolutionStage.ordinal >= EvolutionStage.ADVANCED.ordinal) {
            drawEvolutionAura(petState.evolutionStage, avatarState.animationProgress)
        }
    }

    /**
     * Draw environment background based on evolution stage
     */
    fun DrawScope.drawEnvironmentBackground(
        environment: Environment,
        stage: EvolutionStage
    ) {
        when (environment) {
            Environment.VOID -> {
                // Simple dark gradient
                drawRect(
                    color = Color(0xFF0A0A0A),
                    size = size
                )
            }
            Environment.OFFICE -> {
                // Modern office: floor + window hints
                drawRect(Color(0xFF1A1A1A), size = size) // Dark floor
                drawRect(Color(0xFF2A2A2A), Offset(0f, 0f), Size(size.width, size.height * 0.3f)) // Window hint
            }
            Environment.GARDEN -> {
                // Green tones with grass
                drawRect(Color(0xFF0D1F0D), size = size) // Dark green
                // Simple grass representation
                drawRect(Color(0xFF1A3A1A), Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f))
            }
            Environment.LAB -> {
                // Tech lab: blue tones with grid
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
            }
            Environment.SPACE_STATION -> {
                // Space: deep black with stars
                drawRect(Color.Black, size = size)
                // Draw random stars
                repeat(30) {
                    val x = (0..size.width.toInt()).random().toFloat()
                    val y = (0..size.height.toInt()).random().toFloat()
                    drawCircle(Color.White, radius = 1f, center = Offset(x, y))
                }
            }
        }
    }

    /**
     * Draw stat bars showing health, energy, and happiness
     */
    fun DrawScope.drawStatBars(petState: PixelPetState) {
        val barHeight = 8f
        val barSpacing = 4f
        val barWidth = size.width * 0.8f
        val startX = (size.width - barWidth) / 2f
        val startY = size.height - (barHeight * 3 + barSpacing * 2 + 16f)

        // Health bar (red/green gradient)
        drawStatBar(
            value = petState.health,
            max = 100f,
            color = Color(0xFF4CAF50), // Green
            backgroundColor = Color(0xFF1A1A1A),
            position = Offset(startX, startY),
            barSize = Size(barWidth, barHeight)
        )

        // Energy bar (yellow)
        drawStatBar(
            value = petState.energy,
            max = 100f,
            color = Color(0xFFFFEB3B), // Yellow
            backgroundColor = Color(0xFF1A1A1A),
            position = Offset(startX, startY + barHeight + barSpacing),
            barSize = Size(barWidth, barHeight)
        )

        // Happiness bar (pink)
        drawStatBar(
            value = petState.happiness,
            max = 100f,
            color = Color(0xFFE91E63), // Pink
            backgroundColor = Color(0xFF1A1A1A),
            position = Offset(startX, startY + (barHeight + barSpacing) * 2),
            barSize = Size(barWidth, barHeight)
        )
    }

    /**
     * Draw a single stat bar with value
     */
    private fun DrawScope.drawStatBar(
        value: Float,
        max: Float,
        color: Color,
        backgroundColor: Color,
        position: Offset,
        barSize: Size
    ) {
        val fillRatio = (value / max).coerceIn(0f, 1f)

        // Background
        drawRoundRect(
            color = backgroundColor,
            topLeft = position,
            size = barSize,
            cornerRadius = CornerRadius(barSize.height / 2f)
        )

        // Fill
        if (fillRatio > 0f) {
            drawRoundRect(
                color = color,
                topLeft = position,
                size = Size(barSize.width * fillRatio, barSize.height),
                cornerRadius = CornerRadius(barSize.height / 2f)
            )
        }

        // Border
        drawRoundRect(
            color = color.copy(alpha = 0.5f),
            topLeft = position,
            size = barSize,
            cornerRadius = CornerRadius(barSize.height / 2f),
            style = Stroke(width = 1f)
        )
    }

    /**
     * Draw evolution aura for advanced stages
     */
    fun DrawScope.drawEvolutionAura(stage: EvolutionStage, animationProgress: Float) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val intensity = sin(animationProgress * Math.PI * 2).toFloat() * 0.3f + 0.7f

        val auraColor = when (stage) {
            EvolutionStage.ADVANCED -> Color(0xFF4169E1).copy(alpha = 0.1f * intensity)
            EvolutionStage.EXPERT -> Color(0xFFE25303).copy(alpha = 0.15f * intensity)
            EvolutionStage.LEGENDARY -> Color(0xFFFFD700).copy(alpha = 0.2f * intensity)
            else -> return
        }

        // Pulsing circles
        for (i in 1..3) {
            val radius = 100f + (i * 30f) + (animationProgress * 20f)
            drawCircle(
                color = auraColor,
                radius = radius,
                center = Offset(centerX, centerY)
            )
        }
    }

    /**
     * Draw particle effect
     */
    fun DrawScope.drawParticle(particle: ParticleEffect, animationProgress: Float) {
        val x = size.width * (particle.x / 100f)
        val y = size.height * (particle.y / 100f)

        // Calculate age ratio (0 = just spawned, 1 = about to die)
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val age = currentTime - particle.spawnTime
        val ageRatio = (age.toFloat() / particle.lifetime).coerceIn(0f, 1f)
        val alpha = 1f - ageRatio // Fade out over lifetime

        when (particle.type) {
            ParticleType.WATER_DROPLET -> {
                // Blue droplet falling
                val yOffset = ageRatio * 50f // Fall down
                drawCircle(
                    color = Color(0xFF2196F3).copy(alpha = alpha),
                    radius = 3f,
                    center = Offset(x, y + yOffset)
                )
            }
            ParticleType.ENERGY_SPARKLE -> {
                // Yellow sparkle pulsing
                val pulsScale = 1f + sin(animationProgress * Math.PI * 4).toFloat() * 0.3f
                drawCircle(
                    color = Color(0xFFFFEB3B).copy(alpha = alpha),
                    radius = 4f * pulsScale,
                    center = Offset(x, y)
                )
            }
            ParticleType.CO2_LEAF -> {
                // Green leaf floating up
                val yOffset = -ageRatio * 40f // Float up
                drawCircle(
                    color = Color(0xFF4CAF50).copy(alpha = alpha),
                    radius = 3f,
                    center = Offset(x, y + yOffset)
                )
            }
            ParticleType.HEART -> {
                // Pink heart (interaction feedback)
                drawCircle(
                    color = Color(0xFFE91E63).copy(alpha = alpha),
                    radius = 5f,
                    center = Offset(x, y - ageRatio * 30f) // Rise up
                )
            }
            ParticleType.STAR -> {
                // Gold star (evolution)
                val scale = 1f + (1f - ageRatio) * 2f // Burst outward
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = alpha),
                    radius = 3f * scale,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * Usage Example:
 * ```kotlin
 * // Basic robot avatar
 * Canvas(modifier = Modifier.size(280.dp)) {
 *     with(AvatarEngine) {
 *         drawRobotAvatar(
 *             state = AvatarState(
 *                 emotion = AvatarEmotion.HAPPY,
 *                 activity = AvatarActivity.SPEAKING,
 *                 intensity = 0.8f
 *             )
 *         )
 *     }
 * }
 *
 * // Pixel pet with eco integration
 * Canvas(modifier = Modifier.fillMaxSize()) {
 *     with(AvatarEngine) {
 *         val petState by petViewModel.petState.collectAsState()
 *         val particles by petViewModel.particleEffects.collectAsState()
 *
 *         drawPixelPet(
 *             petState = petState,
 *             avatarState = AvatarState.fromActivity(AvatarActivity.IDLE),
 *             showStatBars = true,
 *             showEnvironment = true
 *         )
 *
 *         // Draw particles on top
 *         particles.forEach { particle ->
 *             drawParticle(particle, animationProgress)
 *         }
 *     }
 * }
 * ```
 */
