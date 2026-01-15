package app.m1k3.ai.assistant.avatar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarEngine.drawPixelPet
import app.m1k3.ai.assistant.avatar.AvatarEngine.drawParticle
import app.m1k3.ai.assistant.avatar.AvatarEngine.drawRobotAvatar
import app.m1k3.ai.assistant.avatar.ui.PetInteractionOverlay
import app.m1k3.ai.assistant.avatar.ui.PetStatsBar
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 間 AI Avatar View
 *
 * Main avatar display component (280dp).
 * Integrates all avatar systems:
 * - Canvas rendering (AvatarEngine)
 * - Emotion detection
 * - Activity animations
 * - State management (AvatarViewModel)
 */

/**
 * Full-size avatar display with emotion and activity indicators
 *
 * Supports both 2D Canvas and 3D model rendering:
 * - Android: 3D Colobus monkey by default (with 2D fallback)
 * - Other platforms: 2D Canvas robot (3D not yet supported)
 *
 * @param state Current avatar state (emotion, activity, intensity)
 * @param modifier Optional modifier
 * @param showInfo Whether to show emotion/activity labels
 * @param onClick Optional click handler for interactive demos
 * @param use3D Whether to use 3D model (Android only, defaults to 2D for compatibility)
 */
@Composable
fun AvatarView(
    state: AvatarState,
    modifier: Modifier = Modifier,
    showInfo: Boolean = true,
    onClick: (() -> Unit)? = null,
    use3D: Boolean = false  // Default to 2D for stability
) {
    // Animate state transitions
    val animatedState = rememberAnimatedAvatarState(
        targetState = state,
        transitionDuration = 300
    )

    // Activity-based animations
    val activityAnim = rememberActivityAnimation(state.activity)

    // Entrance animation (plays once on mount)
    val entranceProgress = rememberEntranceAnimation()

    // Bounce animation for active states
    val bounceOffset = rememberBounceAnimation(state.isAnimating)

    MaCard(
        modifier = modifier
            .size(280.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar rendering (2D Canvas or 3D model)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(activityAnim.scale * entranceProgress)
                    .graphicsLayer {
                        rotationZ = activityAnim.rotation
                        translationX = activityAnim.offsetX
                        translationY = activityAnim.offsetY + bounceOffset
                    },
                contentAlignment = Alignment.Center
            ) {
                if (use3D) {
                    // 3D model rendering (Android only)
                    // Defined in Avatar3DView.android.kt via expect/actual
                    AvatarViewContent3D(state = animatedState)
                } else {
                    // 2D Canvas rendering (all platforms)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRobotAvatar(
                            state = animatedState,
                            geometry = RobotGeometry(),
                            animation = AvatarAnimation()
                        )
                    }
                }
            }

            if (showInfo) {
                Spacer(modifier = Modifier.height(MaSpacing.base))

                // Emotion label
                Text(
                    text = "${state.emotion.emoji} ${state.emotion.displayName}",
                    style = MaTypography.titleMedium,
                    color = state.displayColor,
                    fontWeight = FontWeight.Bold
                )

                // Activity label
                if (state.activity != AvatarActivity.IDLE) {
                    Text(
                        text = state.activity.displayName,
                        style = MaTypography.bodySmall,
                        color = MaColors.textSecondary()
                    )
                }

                // Status message
                if (state.message != null) {
                    Spacer(modifier = Modifier.height(MaSpacing.xs))
                    Text(
                        text = state.message,
                        style = MaTypography.labelSmall,
                        color = MaColors.textDisabled()
                    )
                }

                // Intensity indicator
                Text(
                    text = "Intensity: ${(state.intensity * 100).toInt()}%",
                    style = MaTypography.labelSmall,
                    color = MaColors.textDisabled()
                )
            }
        }
    }
}

/**
 * Compact avatar display (200dp)
 *
 * Smaller version without labels, for headers/toolbars.
 *
 * @param state Current avatar state
 * @param modifier Optional modifier
 */
@Composable
fun AvatarViewCompact(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    val animatedState = rememberAnimatedAvatarState(state)
    val activityAnim = rememberActivityAnimation(state.activity)

    Box(
        modifier = modifier
            .size(200.dp)
            .scale(activityAnim.scale)
            .graphicsLayer {
                rotationZ = activityAnim.rotation
                translationX = activityAnim.offsetX
                translationY = activityAnim.offsetY
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRobotAvatar(
                state = animatedState,
                geometry = RobotGeometry(),
                animation = AvatarAnimation()
            )
        }
    }
}

/**
 * Avatar emotion selector
 *
 * Interactive grid for testing/selecting emotions.
 *
 * @param onEmotionSelected Callback when emotion is selected
 * @param modifier Optional modifier
 */
@Composable
fun AvatarEmotionSelector(
    onEmotionSelected: (AvatarEmotion) -> Unit,
    modifier: Modifier = Modifier
) {
    val emotions = remember {
        listOf(
            AvatarEmotion.HAPPY,
            AvatarEmotion.SAD,
            AvatarEmotion.ANGRY,
            AvatarEmotion.SURPRISED,
            AvatarEmotion.LOVE,
            AvatarEmotion.THINKING,
            AvatarEmotion.SLEEPY,
            AvatarEmotion.EXCITED,
            AvatarEmotion.NEUTRAL
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
    ) {
        Text(
            text = "Avatar Emotions",
            style = MaTypography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaColors.textPrimary()
        )

        // Grid of emotion buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
        ) {
            emotions.chunked(3).forEach { row ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                ) {
                    row.forEach { emotion ->
                        MaCard(
                            onClick = { onEmotionSelected(emotion) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaSpacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = emotion.emoji,
                                    style = MaTypography.headlineSmall
                                )
                                Text(
                                    text = emotion.displayName,
                                    style = MaTypography.labelSmall,
                                    color = emotion.primaryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Avatar activity indicator
 *
 * Shows current AI activity with animation.
 *
 * @param activity Current activity
 * @param modifier Optional modifier
 */
@Composable
fun AvatarActivityIndicator(
    activity: AvatarActivity,
    modifier: Modifier = Modifier
) {
    val activityAnim = rememberActivityAnimation(activity)
    val idleColor = MaColors.textDisabled()

    Row(
        modifier = modifier
            .scale(activityAnim.scale)
            .padding(horizontal = MaSpacing.base, vertical = MaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Activity indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer {
                    alpha = if (activity.isActive) 1f else 0.3f
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = when (activity) {
                        AvatarActivity.LISTENING -> MaColors.Info
                        AvatarActivity.THINKING -> MaColors.Warning
                        AvatarActivity.GENERATING -> MaColors.Orange
                        AvatarActivity.SPEAKING -> MaColors.Success
                        AvatarActivity.ERROR -> MaColors.Error
                        AvatarActivity.IDLE -> idleColor
                    }
                )
            }
        }

        Text(
            text = activity.displayName,
            style = MaTypography.bodySmall,
            color = if (activity.isActive) MaColors.textPrimary() else MaColors.textDisabled(),
            fontWeight = if (activity.isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Pixel Pet View - Eco-Integrated Virtual Companion
 *
 * Complete tamagotchi-style pixel pet with eco credit integration.
 * Combines:
 * - Canvas rendering with drawPixelPet()
 * - Real-time particle effects (water, energy, CO2)
 * - Touch gesture interactions (pat, double-tap, long-press)
 * - Stat bars with eco tooltips
 * - Evolution notification banners
 *
 * **Eco Integration:**
 * - Water saved → Health
 * - Energy saved → Energy
 * - CO2 prevented → Happiness
 * - Achievements unlock evolution stages
 *
 * @param petState Current pixel pet state
 * @param avatarState Avatar emotional state (for rendering)
 * @param petViewModel ViewModel for interactions
 * @param modifier Optional modifier
 * @param showStatBars Whether to show stat bars below pet
 * @param showEnvironment Whether to render background environment
 * @param enableInteractions Whether touch gestures are active
 */
@OptIn(ExperimentalTime::class)
@Composable
fun PixelPetView(
    petState: PixelPetState,
    avatarState: AvatarState,
    petViewModel: PetViewModel,
    modifier: Modifier = Modifier,
    showStatBars: Boolean = true,
    showEnvironment: Boolean = true,
    enableInteractions: Boolean = true,
    showPixelGrid: Boolean = false,
    showResolutionDebug: Boolean = false,
    useRoundedPixels: Boolean = true
) {
    // Get system theme setting for theme-aware colors
    val isDarkMode = isSystemInDarkTheme()

    // Collect particle effects
    val particleEffects by petViewModel.particleEffects.collectAsState()

    // Evolution notification state
    var showEvolutionNotification by remember { mutableStateOf<EvolutionStage?>(null) }

    // Listen for evolution changes
    LaunchedEffect(petState.evolutionStage) {
        if (petState.evolutionStage.ordinal > 0) {
            showEvolutionNotification = petState.evolutionStage
            kotlinx.coroutines.delay(3000)
            showEvolutionNotification = null
        }
    }

    Box(modifier = modifier) {
        // Main pixel pet rendering
        MaCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaSpacing.base),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pet canvas with particles
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .padding(MaSpacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    // Canvas rendering layer
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawPixelPet(
                            petState = petState,
                            avatarState = avatarState,
                            geometry = RobotGeometry(),
                            animation = AvatarAnimation(),
                            showStatBars = false,  // Stats shown separately below
                            showEnvironment = showEnvironment,
                            showPixelGrid = showPixelGrid,
                            showResolutionDebug = showResolutionDebug,
                            useRoundedPixels = useRoundedPixels,
                            isDarkMode = isDarkMode
                        )

                        // Render particles (use current timestamp as animationProgress)
                        val now = Clock.System.now().toEpochMilliseconds()
                        val animProgress = ((now % 1000) / 1000f) // 0-1 loop every second
                        particleEffects.forEach { particle ->
                            drawParticle(particle, animProgress)
                        }
                    }

                    // Interaction overlay (transparent touch layer)
                    if (enableInteractions) {
                        PetInteractionOverlay(
                            petViewModel = petViewModel,
                            enabled = true,
                            showFeedback = true
                        )
                    }
                }

                // Stat bars with eco tooltips
                if (showStatBars) {
                    Spacer(modifier = Modifier.height(MaSpacing.base))
                    PetStatsBar(
                        petState = petState,
                        showEcoTooltips = true,
                        compact = false
                    )
                }

                // Current achievement display
                if (petState.currentAchievement != null) {
                    Spacer(modifier = Modifier.height(MaSpacing.sm))
                    Text(
                        text = "🏆 ${petState.currentAchievement}",
                        style = MaTypography.labelSmall,
                        color = MaColors.Orange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Evolution notification banner
        AnimatedVisibility(
            visible = showEvolutionNotification != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            showEvolutionNotification?.let { stage ->
                MaCard(
                    modifier = Modifier
                        .padding(MaSpacing.base)
                        .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaSpacing.base),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨",  // Evolution star emoji
                            style = MaTypography.headlineMedium
                        )
                        Spacer(modifier = Modifier.width(MaSpacing.sm))
                        Column {
                            Text(
                                text = "Evolution!",
                                style = MaTypography.titleSmall,
                                color = MaColors.Orange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stage.displayName,
                                style = MaTypography.bodySmall,
                                color = MaColors.textSecondary()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact Pixel Pet View (for small displays)
 *
 * Minimal version without stat bars or interaction feedback.
 * Perfect for headers, toolbars, or floating displays.
 *
 * @param petState Current pixel pet state
 * @param avatarState Avatar emotional state
 * @param modifier Optional modifier
 */
@Composable
fun PixelPetViewCompact(
    petState: PixelPetState,
    avatarState: AvatarState,
    modifier: Modifier = Modifier
) {
    val isDarkMode = isSystemInDarkTheme()

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPixelPet(
                petState = petState,
                avatarState = avatarState,
                geometry = RobotGeometry(),
                animation = AvatarAnimation(),
                showStatBars = false,
                showEnvironment = false,
                isDarkMode = isDarkMode
            )
        }
    }
}

/**
 * Platform-specific 3D avatar content
 *
 * On Android: Renders Colobus 3D model with SceneView
 * On other platforms: Falls back to 2D Canvas (3D not yet supported)
 *
 * @param state Avatar state to render
 */
@Composable
expect fun AvatarViewContent3D(
    state: AvatarState
)

/**
 * Usage Examples:
 * ```kotlin
 * // ========================================
 * // Classic Avatar Usage
 * // ========================================
 *
 * @Composable
 * fun AvatarDemo() {
 *     val viewModel = rememberAvatarViewModel()
 *     val state by viewModel.collectAsState()
 *
 *     Column {
 *         // Main avatar display
 *         AvatarView(
 *             state = state,
 *             showInfo = true,
 *             onClick = { viewModel.flashEmotion(AvatarEmotion.EXCITED) }
 *         )
 *
 *         // Emotion selector
 *         AvatarEmotionSelector(
 *             onEmotionSelected = { viewModel.setEmotion(it, 0.8f) }
 *         )
 *
 *         // Activity indicator
 *         AvatarActivityIndicator(activity = state.activity)
 *     }
 * }
 *
 * @Composable
 * fun ChatScreenWithAvatar() {
 *     val viewModel = rememberAvatarViewModel()
 *
 *     // Sync with AI
 *     LaunchedEffect(isGenerating) {
 *         viewModel.syncWithAI(isGenerating)
 *     }
 *
 *     // Compact avatar in header
 *     AvatarViewCompact(
 *         state = viewModel.avatarState.collectAsState().value,
 *         modifier = Modifier.size(80.dp)
 *     )
 * }
 *
 * // ========================================
 * // Pixel Pet Usage (Eco-Integrated)
 * // ========================================
 *
 * @Composable
 * fun PixelPetDemo() {
 *     val database = remember { MaDatabase(driver) }
 *     val ecoRepo = remember { EcoMetricsRepository(database) }
 *     val scope = rememberCoroutineScope()
 *     val petViewModel = remember { PetViewModel(ecoRepo, scope) }
 *
 *     val petState by petViewModel.petState.collectAsState()
 *     val avatarState = remember { AvatarState(emotion = AvatarEmotion.HAPPY) }
 *
 *     // Full pixel pet display
 *     PixelPetView(
 *         petState = petState,
 *         avatarState = avatarState,
 *         petViewModel = petViewModel,
 *         showStatBars = true,
 *         showEnvironment = true,
 *         enableInteractions = true
 *     )
 * }
 *
 * @Composable
 * fun ChatScreenWithPixelPet() {
 *     val database = remember { MaDatabase(driver) }
 *     val ecoRepo = remember { EcoMetricsRepository(database) }
 *     val scope = rememberCoroutineScope()
 *     val petViewModel = remember { PetViewModel(ecoRepo, scope) }
 *
 *     // Create ChatViewModel with pet integration
 *     val chatViewModel = remember {
 *         ChatViewModel(
 *             database = database,
 *             projectId = "default",
 *             scope = scope,
 *             petViewModel = petViewModel  // Links eco metrics → pixel pet
 *         )
 *     }
 *
 *     val petState by petViewModel.petState.collectAsState()
 *     val avatarState = remember { AvatarState() }
 *
 *     Column {
 *         // Compact pet in header
 *         PixelPetViewCompact(
 *             petState = petState,
 *             avatarState = avatarState,
 *             modifier = Modifier.padding(8.dp)
 *         )
 *
 *         // Chat messages
 *         // ... chat UI ...
 *
 *         // Stat bars at bottom
 *         PetStatsBar(
 *             petState = petState,
 *             showEcoTooltips = true,
 *             compact = true
 *         )
 *     }
 * }
 *
 * @Composable
 * fun PetDashboard() {
 *     val database = remember { MaDatabase(driver) }
 *     val petRepo = remember { PetMetricsRepository(database) }
 *     val petState = remember { petRepo.loadLatestState() ?: PixelPetState() }
 *
 *     Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
 *         // Pet display
 *         PixelPetView(
 *             petState = petState,
 *             avatarState = AvatarState(),
 *             petViewModel = petViewModel,
 *             modifier = Modifier.fillMaxWidth().height(400.dp)
 *         )
 *
 *         Spacer(modifier = Modifier.height(16.dp))
 *
 *         // Analytics
 *         val summary = remember { petRepo.getActivitySummary() }
 *         Text("Total Conversations: ${summary.totalConversations}")
 *         Text("Water Saved: ${EcoCalculator.formatWater(summary.totalWaterSavedMl.toInt())}")
 *         Text("Evolution: ${summary.highestEvolution.displayName}")
 *
 *         // Evolution history
 *         val history = remember { petRepo.getEvolutionHistory() }
 *         history.forEach { entry ->
 *             Text("${entry.stage.emoji} ${entry.stage.displayName} - ${entry.achievement}")
 *         }
 *     }
 * }
 * ```
 */
