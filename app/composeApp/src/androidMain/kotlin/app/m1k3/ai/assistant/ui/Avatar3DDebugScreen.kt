package app.m1k3.ai.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.avatar.demo.PixelPetDemoScreen
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaButtonSecondary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * 間 AI Avatar 3D Debug Screen
 *
 * Comprehensive testing interface for the 3D avatar system:
 * - Toggle between 2D Canvas and 3D Filament rendering
 * - Select from 9 models (8 Quirky Series animals + Mask)
 * - Interactive camera controls (pinch-zoom, orbit, pan)
 * - Model metadata viewer
 * - Test all 9 emotions with intensity control
 * - Test all 6 activities
 * - Real-time state visualization
 * - Performance metrics
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

    // Avatar state
    var currentEmotion by remember { mutableStateOf(AvatarEmotion.NEUTRAL) }
    var currentActivity by remember { mutableStateOf(AvatarActivity.IDLE) }
    var intensity by remember { mutableFloatStateOf(0.5f) }
    var use3D by remember { mutableStateOf(true) }  // DEFAULT: Filament 3D (production-ready)
    var showAdvanced by remember { mutableStateOf(false) }

    // NEW: 3D model selection and camera controls
    var selectedModel by remember { mutableStateOf(ModelRegistry.getDefault()) }
    var enableInteraction by remember { mutableStateOf(true) }
    var showModelInfo by remember { mutableStateOf(false) }

    // Avatar state - recreates when any dependency changes
    val avatarState = remember(currentEmotion, currentActivity, intensity, use3D, selectedModel) {
        AvatarState(
            emotion = currentEmotion,
            activity = currentActivity,
            intensity = intensity,
            message = if (use3D) selectedModel.name else "2D Canvas Robot"
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "🎨 Avatar Debug",
                                style = MaTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.TextPrimary
                            )
                            Text(
                                if (use3D) "Testing ${selectedModel.name} • ${if (enableInteraction) "Interactive" else "Static"}" else "Testing 2D Canvas Robot",
                                style = MaTypography.bodySmall,
                                color = MaColors.Orange
                            )
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onBackClick) {
                            Text("← Back", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaColors.BgPrimary
                    )
                )
            }
        }
    ) { padding ->
        // Main content
        AvatarDebugContent(
            padding = padding,
            currentEmotion = currentEmotion,
            onEmotionChange = { currentEmotion = it },
            currentActivity = currentActivity,
            onActivityChange = { currentActivity = it },
            intensity = intensity,
            onIntensityChange = { intensity = it },
            use3D = use3D,
            onUse3DChange = { use3D = it },
            showAdvanced = showAdvanced,
            onShowAdvancedChange = { showAdvanced = it },
            selectedModel = selectedModel,
            onModelChange = { selectedModel = it },
            enableInteraction = enableInteraction,
            onEnableInteractionChange = { enableInteraction = it },
            showModelInfo = showModelInfo,
            onShowModelInfoChange = { showModelInfo = it },
            avatarState = avatarState,
            haptics = haptics
        )
    }
}

@Composable
private fun AvatarDebugContent(
    padding: PaddingValues,
    currentEmotion: AvatarEmotion,
    onEmotionChange: (AvatarEmotion) -> Unit,
    currentActivity: AvatarActivity,
    onActivityChange: (AvatarActivity) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    use3D: Boolean,
    onUse3DChange: (Boolean) -> Unit,
    showAdvanced: Boolean,
    onShowAdvancedChange: (Boolean) -> Unit,
    selectedModel: ModelConfig,
    onModelChange: (ModelConfig) -> Unit,
    enableInteraction: Boolean,
    onEnableInteractionChange: (Boolean) -> Unit,
    showModelInfo: Boolean,
    onShowModelInfoChange: (Boolean) -> Unit,
    avatarState: AvatarState,
    haptics: app.m1k3.ai.assistant.design.haptics.HapticFeedbackController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(MaSpacing.base),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
    ) {
            // Avatar Display
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar (with 3D model support)
                    if (use3D) {
                        Avatar3DView(
                            state = avatarState,
                            modelConfig = selectedModel,
                            enableInteraction = enableInteraction,
                            modifier = Modifier.size(280.dp)
                        )
                    } else {
                        AvatarView(
                            state = avatarState,
                            showInfo = true,
                            use3D = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(MaSpacing.sm))

                    // Current state info
                    Text(
                        text = "${currentEmotion.emoji} ${currentEmotion.displayName} • ${currentActivity.displayName}",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = avatarState.displayColor
                    )
                }
            }

            // 2D/3D Toggle
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Rendering Mode",
                            style = MaTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary
                        )
                        Text(
                            if (use3D) "3D Model (SceneView + Filament)" else "2D Canvas (Compose Drawing)",
                            style = MaTypography.bodySmall,
                            color = MaColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = use3D,
                        onCheckedChange = {
                            onUse3DChange(it)
                            haptics.performHapticFeedback(HapticFeedbackType.MEDIUM)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaColors.Orange,
                            checkedTrackColor = MaColors.Orange.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // Model Selector & Camera Controls (3D only)
            AnimatedVisibility(visible = use3D) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
                ) {
                    // Model Selector
                    MaCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaSpacing.base)
                        ) {
                            Text(
                                "3D Model (${ModelRegistry.allModels.size} Animals)",
                                style = MaTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(MaSpacing.sm))

                            // Model buttons grid (2 columns)
                            ModelRegistry.allModels.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                                ) {
                                    row.forEach { model ->
                                        if (selectedModel.id == model.id) {
                                            MaButtonPrimary(
                                                text = model.name,
                                                onClick = {},
                                                modifier = Modifier.weight(1f),
                                                enabled = false  // Selected
                                            )
                                        } else {
                                            MaButtonSecondary(
                                                text = model.name,
                                                onClick = {
                                                    onModelChange(model)
                                                    haptics.performHapticFeedback(HapticFeedbackType.MEDIUM)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    // Fill empty space if odd number
                                    if (row.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(MaSpacing.xs))
                            }

                            // Model info toggle
                            Spacer(modifier = Modifier.height(MaSpacing.xs))
                            MaButtonSecondary(
                                text = if (showModelInfo) "▼ Hide Model Info" else "▶ Show Model Info",
                                onClick = {
                                    onShowModelInfoChange(!showModelInfo)
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Model metadata display
                            AnimatedVisibility(visible = showModelInfo) {
                                Column(
                                    modifier = Modifier.padding(top = MaSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                                ) {
                                    HorizontalDivider(color = MaColors.BorderLight)
                                    Spacer(modifier = Modifier.height(MaSpacing.xs))

                                    DebugInfoRow("ID", selectedModel.id)
                                    DebugInfoRow("Path", selectedModel.path)
                                    DebugInfoRow("Category", selectedModel.category)
                                    if (selectedModel.description.isNotEmpty()) {
                                        Text(
                                            text = selectedModel.description,
                                            style = MaTypography.bodySmall,
                                            color = MaColors.TextSecondary,
                                            modifier = Modifier.padding(top = MaSpacing.xs)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Camera Controls
                    MaCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaSpacing.base)
                        ) {
                            Text(
                                "Camera Controls",
                                style = MaTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.TextPrimary
                            )
                            Text(
                                "Interactive gestures: pinch-zoom, drag-orbit, two-finger-pan, double-tap-reset",
                                style = MaTypography.bodySmall,
                                color = MaColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(MaSpacing.sm))

                            // Enable interaction toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Enable Touch Controls",
                                        style = MaTypography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaColors.TextPrimary
                                    )
                                    Text(
                                        if (enableInteraction) "Touch enabled (pinch/drag)" else "Touch disabled (static view)",
                                        style = MaTypography.labelSmall,
                                        color = MaColors.TextSecondary
                                    )
                                }
                                Switch(
                                    checked = enableInteraction,
                                    onCheckedChange = {
                                        onEnableInteractionChange(it)
                                        haptics.performHapticFeedback(HapticFeedbackType.MEDIUM)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaColors.Orange,
                                        checkedTrackColor = MaColors.Orange.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Emotions Grid
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base)
                ) {
                    Text(
                        "Emotions (9)",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(MaSpacing.sm))

                    // 3x3 grid
                    val emotions = listOf(
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

                    emotions.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                        ) {
                            row.forEach { emotion ->
                                if (currentEmotion == emotion) {
                                    MaButtonPrimary(
                                        text = "${emotion.emoji} ${emotion.displayName}",
                                        onClick = {
                                            onEmotionChange(emotion)
                                            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = false  // Selected
                                    )
                                } else {
                                    MaButtonSecondary(
                                        text = "${emotion.emoji} ${emotion.displayName}",
                                        onClick = {
                                            onEmotionChange(emotion)
                                            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(MaSpacing.xs))
                    }
                }
            }

            // Activities Row
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base)
                ) {
                    Text(
                        "Activities (6)",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(MaSpacing.sm))

                    val activities = listOf(
                        AvatarActivity.IDLE,
                        AvatarActivity.LISTENING,
                        AvatarActivity.THINKING,
                        AvatarActivity.GENERATING,
                        AvatarActivity.SPEAKING,
                        AvatarActivity.ERROR
                    )

                    activities.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                        ) {
                            row.forEach { activity ->
                                if (currentActivity == activity) {
                                    MaButtonPrimary(
                                        text = activity.displayName,
                                        onClick = {
                                            onActivityChange(activity)
                                            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = false  // Selected
                                    )
                                } else {
                                    MaButtonSecondary(
                                        text = activity.displayName,
                                        onClick = {
                                            onActivityChange(activity)
                                            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(MaSpacing.xs))
                    }
                }
            }

            // Intensity Slider
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base)
                ) {
                    Text(
                        "Intensity: ${(intensity * 100).toInt()}%",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary
                    )
                    Text(
                        "Affects animation speed (0.5x - 1.5x) and color brightness",
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(MaSpacing.sm))

                    Slider(
                        value = intensity,
                        onValueChange = { onIntensityChange(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaColors.Orange,
                            activeTrackColor = MaColors.Orange
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtle", style = MaTypography.labelSmall, color = MaColors.TextDisabled)
                        Text("Extreme", style = MaTypography.labelSmall, color = MaColors.TextDisabled)
                    }
                }
            }

            // Advanced Debug Info
            MaCard(
                onClick = { onShowAdvancedChange(!showAdvanced) },
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
                            if (showAdvanced) "▼" else "▶",
                            style = MaTypography.titleMedium,
                            color = MaColors.TextSecondary
                        )
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column(
                            modifier = Modifier.padding(top = MaSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                        ) {
                            Divider(color = MaColors.BorderLight)
                            Spacer(modifier = Modifier.height(MaSpacing.xs))

                            DebugInfoRow("Emotion", currentEmotion.name)
                            DebugInfoRow("Activity", currentActivity.name)
                            DebugInfoRow("Intensity", "${(intensity * 100).toInt()}%")
                            DebugInfoRow("Display Color", avatarState.displayColor.toString())
                            DebugInfoRow("Is Animating", avatarState.isAnimating.toString())

                            if (use3D) {
                                HorizontalDivider(color = MaColors.BorderLight, modifier = Modifier.padding(vertical = MaSpacing.xs))

                                // Model info
                                Text(
                                    "3D Model Info",
                                    style = MaTypography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaColors.TextPrimary
                                )
                                DebugInfoRow("Model", selectedModel.name)
                                DebugInfoRow("Model ID", selectedModel.id)
                                DebugInfoRow("Category", selectedModel.category)
                                DebugInfoRow("Interactive", enableInteraction.toString())

                                // Animation info
                                Spacer(modifier = Modifier.height(MaSpacing.xs))
                                val animInfo = Avatar3DEngine.getAnimation(avatarState)
                                Text(
                                    "Current Animation",
                                    style = MaTypography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaColors.TextPrimary
                                )
                                DebugInfoRow("Animation", animInfo.name)
                                DebugInfoRow("Speed", "${Avatar3DEngine.getAnimationSpeed(intensity)}x")
                                DebugInfoRow("Loopable", animInfo.loopable.toString())
                                DebugInfoRow("Duration", "${animInfo.duration}s")
                            }
                        }
                    }
                }
            }

            // Quick Test Buttons
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base)
                ) {
                    Text(
                        "Quick Tests",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(MaSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                    ) {
                        MaButtonPrimary(
                            text = "😊 Happy Path",
                            onClick = {
                                onEmotionChange(AvatarEmotion.HAPPY)
                                onActivityChange(AvatarActivity.SPEAKING)
                                onIntensityChange(0.8f)
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MaButtonPrimary(
                            text = "🤔 Thinking",
                            onClick = {
                                onEmotionChange(AvatarEmotion.THINKING)
                                onActivityChange(AvatarActivity.THINKING)
                                onIntensityChange(0.6f)
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(MaSpacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                    ) {
                        MaButtonPrimary(
                            text = "⚡ Generating",
                            onClick = {
                                onEmotionChange(AvatarEmotion.EXCITED)
                                onActivityChange(AvatarActivity.GENERATING)
                                onIntensityChange(0.9f)
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MaButtonSecondary(
                            text = "❌ Error",
                            onClick = {
                                onEmotionChange(AvatarEmotion.ANGRY)
                                onActivityChange(AvatarActivity.ERROR)
                                onIntensityChange(1.0f)
                                haptics.performHapticFeedback(HapticFeedbackType.ERROR)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Animation Tests (only show in 3D mode)
            AnimatedVisibility(visible = use3D) {
                MaCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                    onActivityChange(AvatarActivity.LISTENING)  // → Idle_A
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MaButtonSecondary(
                                text = "Idle B",
                                onClick = {
                                    onEmotionChange(AvatarEmotion.HAPPY)  // → Idle_B
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MaButtonSecondary(
                                text = "Idle C",
                                onClick = {
                                    onActivityChange(AvatarActivity.IDLE)  // → Idle_C
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
                                text = "🌀 Spin",
                                onClick = {
                                    onActivityChange(AvatarActivity.THINKING)  // → Spin
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MaButtonSecondary(
                                text = "🏃 Run",
                                onClick = {
                                    onActivityChange(AvatarActivity.GENERATING)  // → Run
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MaButtonSecondary(
                                text = "⚡ Bounce",
                                onClick = {
                                    onEmotionChange(AvatarEmotion.EXCITED)  // → Bounce
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
                                text = "💬 Clicked",
                                onClick = {
                                    onActivityChange(AvatarActivity.SPEAKING)  // → Clicked
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MaButtonSecondary(
                                text = "🪑 Sit",
                                onClick = {
                                    onEmotionChange(AvatarEmotion.SAD)  // → Sit
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MaButtonSecondary(
                                text = "⚔️ Attack",
                                onClick = {
                                    onEmotionChange(AvatarEmotion.ANGRY)  // → Attack
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun DebugInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaTypography.bodySmall,
            color = MaColors.TextSecondary
        )
        Text(
            text = value,
            style = MaTypography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaColors.TextPrimary
        )
    }
}
