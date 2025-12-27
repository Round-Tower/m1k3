package app.m1k3.ai.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.avatar.debug.collectAsState
import app.m1k3.ai.assistant.avatar.debug.rememberAvatarDebugViewModel
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.components.MaButtonSecondary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.ui.components.*

/**
 * Avatar3DDebugScreen - Comprehensive testing interface for the 3D avatar system
 *
 * **Features:**
 * - Toggle between 2D Canvas and 3D Filament rendering
 * - Select from 9 models (8 Quirky Series animals + Mask)
 * - Interactive camera controls (pinch-zoom, orbit, pan)
 * - Model metadata viewer
 * - Test all 9 emotions with intensity control
 * - Test all 6 activities
 * - Real-time state visualization
 * - Performance metrics
 *
 * **Architecture:**
 * - Uses AvatarDebugViewModel for state management
 * - Delegates to extracted components (EmotionsGridCard, ActivitiesGridCard, etc.)
 * - Minimal UI logic - ViewModel handles business logic
 *
 * Speeds up development and polish by allowing rapid iteration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Avatar3DDebugScreen(
    database: MaDatabase,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHapticFeedback()

    // AvatarDebugViewModel - Single source of truth for debug state
    val viewModel = rememberAvatarDebugViewModel()
    val state by viewModel.collectAsState()
    val avatarState = viewModel.getAvatarState()

    Scaffold(
        topBar = {
            AvatarDebugTopBar(
                use3D = state.use3D,
                selectedModel = state.selectedModel,
                enableInteraction = state.enableInteraction,
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
        ) {
            // Avatar Display
            AvatarDisplayCard(
                avatarState = avatarState,
                use3D = state.use3D,
                selectedModel = state.selectedModel,
                enableInteraction = state.enableInteraction,
                currentEmotion = state.currentEmotion,
                currentActivity = state.currentActivity
            )

            // 2D/3D Toggle
            RenderingModeCard(
                use3D = state.use3D,
                onUse3DChange = { viewModel.setUse3D(it) },
                haptics = haptics
            )

            // Model Selector & Camera Controls (3D only)
            AnimatedVisibility(visible = state.use3D) {
                Column(verticalArrangement = Arrangement.spacedBy(MaSpacing.base)) {
                    ModelSelectorCard(
                        selectedModel = state.selectedModel,
                        onModelChange = { viewModel.setSelectedModel(it) },
                        showModelInfo = state.showModelInfo,
                        onToggleModelInfo = { viewModel.toggleShowModelInfo() },
                        haptics = haptics
                    )

                    CameraControlsCard(
                        enableInteraction = state.enableInteraction,
                        onEnableInteractionChange = { viewModel.setEnableInteraction(it) },
                        haptics = haptics
                    )
                }
            }

            // Emotions Grid
            EmotionsGridCard(
                currentEmotion = state.currentEmotion,
                onEmotionChange = { viewModel.setEmotion(it) },
                haptics = haptics
            )

            // Activities Row
            ActivitiesGridCard(
                currentActivity = state.currentActivity,
                onActivityChange = { viewModel.setActivity(it) },
                haptics = haptics
            )

            // Intensity Slider
            IntensitySliderCard(
                intensity = state.intensity,
                onIntensityChange = { viewModel.setIntensity(it) }
            )

            // Advanced Debug Info
            AdvancedDebugInfoCard(
                showAdvanced = state.showAdvanced,
                onToggleAdvanced = { viewModel.toggleShowAdvanced() },
                avatarState = avatarState,
                state = state
            )

            // Quick Test Buttons
            QuickTestsCard(
                onHappyPath = { viewModel.applyHappyPath() },
                onThinking = { viewModel.applyThinking() },
                onGenerating = { viewModel.applyGenerating() },
                onError = { viewModel.applyError() },
                haptics = haptics
            )

            // Animation Tests (only show in 3D mode)
            AnimatedVisibility(visible = state.use3D) {
                AnimationTestsCard(
                    onActivityChange = { viewModel.setActivity(it) },
                    onEmotionChange = { viewModel.setEmotion(it) },
                    haptics = haptics
                )
            }
        }
    }
}

/**
 * AvatarDebugTopBar - Top app bar for avatar debug screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarDebugTopBar(
    use3D: Boolean,
    selectedModel: ModelConfig,
    enableInteraction: Boolean,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "Avatar Debug",
                    style = MaTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.TextPrimary
                )
                Text(
                    if (use3D) {
                        "Testing ${selectedModel.name} - ${if (enableInteraction) "Interactive" else "Static"}"
                    } else {
                        "Testing 2D Canvas Robot"
                    },
                    style = MaTypography.bodySmall,
                    color = MaColors.Orange
                )
            }
        },
        navigationIcon = {
            TextButton(onClick = onBackClick) {
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaColors.BgPrimary
        )
    )
}

/**
 * AdvancedDebugInfoCard - Expandable debug info section.
 */
@Composable
private fun AdvancedDebugInfoCard(
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    avatarState: AvatarState,
    state: app.m1k3.ai.assistant.avatar.debug.AvatarDebugState
) {
    MaCard(
        onClick = onToggleAdvanced,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Advanced Debug Info",
                    style = MaTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.TextPrimary
                )
                Text(
                    if (showAdvanced) "v" else ">",
                    style = MaTypography.titleMedium,
                    color = MaColors.TextSecondary
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier.padding(top = MaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                ) {
                    HorizontalDivider(color = MaColors.BorderLight)
                    Spacer(modifier = Modifier.height(MaSpacing.xs))

                    DebugInfoRow("Emotion", state.currentEmotion.name)
                    DebugInfoRow("Activity", state.currentActivity.name)
                    DebugInfoRow("Intensity", "${(state.intensity * 100).toInt()}%")
                    DebugInfoRow("Display Color", avatarState.displayColor.toString())
                    DebugInfoRow("Is Animating", avatarState.isAnimating.toString())

                    if (state.use3D) {
                        HorizontalDivider(
                            color = MaColors.BorderLight,
                            modifier = Modifier.padding(vertical = MaSpacing.xs)
                        )

                        Text(
                            "3D Model Info",
                            style = MaTypography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary
                        )
                        DebugInfoRow("Model", state.selectedModel.name)
                        DebugInfoRow("Model ID", state.selectedModel.id)
                        DebugInfoRow("Category", state.selectedModel.category)
                        DebugInfoRow("Interactive", state.enableInteraction.toString())

                        Spacer(modifier = Modifier.height(MaSpacing.xs))
                        val animInfo = Avatar3DEngine.getAnimation(avatarState)
                        Text(
                            "Current Animation",
                            style = MaTypography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary
                        )
                        DebugInfoRow("Animation", animInfo.name)
                        DebugInfoRow("Speed", "${Avatar3DEngine.getAnimationSpeed(state.intensity)}x")
                        DebugInfoRow("Loopable", animInfo.loopable.toString())
                        DebugInfoRow("Duration", "${animInfo.duration}s")
                    }
                }
            }
        }
    }
}

/**
 * AnimationTestsCard - Direct animation test buttons.
 */
@Composable
private fun AnimationTestsCard(
    onActivityChange: (AvatarActivity) -> Unit,
    onEmotionChange: (AvatarEmotion) -> Unit,
    haptics: app.m1k3.ai.assistant.design.haptics.HapticFeedbackController
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Text(
                "Animation Tests (Direct)",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )
            Text(
                "Test specific animations instantly",
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Row 1: Idle animations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
            ) {
                MaButtonSecondary(
                    text = "Idle A",
                    onClick = {
                        onActivityChange(AvatarActivity.LISTENING)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Idle B",
                    onClick = {
                        onEmotionChange(AvatarEmotion.HAPPY)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Idle C",
                    onClick = {
                        onActivityChange(AvatarActivity.IDLE)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(MaSpacing.xs))

            // Row 2: Active animations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
            ) {
                MaButtonSecondary(
                    text = "Spin",
                    onClick = {
                        onActivityChange(AvatarActivity.THINKING)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Run",
                    onClick = {
                        onActivityChange(AvatarActivity.GENERATING)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Bounce",
                    onClick = {
                        onEmotionChange(AvatarEmotion.EXCITED)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(MaSpacing.xs))

            // Row 3: Emotive animations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
            ) {
                MaButtonSecondary(
                    text = "Clicked",
                    onClick = {
                        onActivityChange(AvatarActivity.SPEAKING)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Sit",
                    onClick = {
                        onEmotionChange(AvatarEmotion.SAD)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Attack",
                    onClick = {
                        onEmotionChange(AvatarEmotion.ANGRY)
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
