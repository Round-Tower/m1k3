package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaButtonSecondary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackController
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * AvatarDisplayCard - Shows the avatar with current state info.
 */
@Composable
fun AvatarDisplayCard(
    avatarState: AvatarState,
    use3D: Boolean,
    selectedModel: ModelConfig,
    enableInteraction: Boolean,
    currentEmotion: AvatarEmotion,
    currentActivity: AvatarActivity
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            Text(
                text = "${currentEmotion.emoji} ${currentEmotion.displayName} - ${currentActivity.displayName}",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = avatarState.displayColor
            )
        }
    }
}

/**
 * RenderingModeCard - Toggle between 2D and 3D rendering.
 */
@Composable
fun RenderingModeCard(
    use3D: Boolean,
    onUse3DChange: (Boolean) -> Unit,
    haptics: HapticFeedbackController
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
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
}

/**
 * ModelSelectorCard - 3D model selection with info toggle.
 */
@Composable
fun ModelSelectorCard(
    selectedModel: ModelConfig,
    onModelChange: (ModelConfig) -> Unit,
    showModelInfo: Boolean,
    onToggleModelInfo: () -> Unit,
    haptics: HapticFeedbackController
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
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
                                enabled = false
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
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(MaSpacing.xs))
            }

            // Model info toggle
            Spacer(modifier = Modifier.height(MaSpacing.xs))
            MaButtonSecondary(
                text = if (showModelInfo) "Hide Model Info" else "Show Model Info",
                onClick = {
                    onToggleModelInfo()
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
}

/**
 * CameraControlsCard - Camera interaction controls.
 */
@Composable
fun CameraControlsCard(
    enableInteraction: Boolean,
    onEnableInteractionChange: (Boolean) -> Unit,
    haptics: HapticFeedbackController
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
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

/**
 * EmotionsGridCard - Grid of emotion buttons.
 */
@Composable
fun EmotionsGridCard(
    currentEmotion: AvatarEmotion,
    onEmotionChange: (AvatarEmotion) -> Unit,
    haptics: HapticFeedbackController
) {
    val emotions = listOf(
        AvatarEmotion.HAPPY, AvatarEmotion.SAD, AvatarEmotion.ANGRY,
        AvatarEmotion.SURPRISED, AvatarEmotion.LOVE, AvatarEmotion.THINKING,
        AvatarEmotion.SLEEPY, AvatarEmotion.EXCITED, AvatarEmotion.NEUTRAL
    )

    MaCard(modifier = Modifier.fillMaxWidth()) {
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

            emotions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                ) {
                    row.forEach { emotion ->
                        val buttonText = "${emotion.emoji} ${emotion.displayName}"
                        if (currentEmotion == emotion) {
                            MaButtonPrimary(
                                text = buttonText,
                                onClick = {
                                    onEmotionChange(emotion)
                                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = false
                            )
                        } else {
                            MaButtonSecondary(
                                text = buttonText,
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
}

/**
 * ActivitiesGridCard - Grid of activity buttons.
 */
@Composable
fun ActivitiesGridCard(
    currentActivity: AvatarActivity,
    onActivityChange: (AvatarActivity) -> Unit,
    haptics: HapticFeedbackController
) {
    val activities = listOf(
        AvatarActivity.IDLE, AvatarActivity.LISTENING, AvatarActivity.THINKING,
        AvatarActivity.GENERATING, AvatarActivity.SPEAKING, AvatarActivity.ERROR
    )

    MaCard(modifier = Modifier.fillMaxWidth()) {
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
                                enabled = false
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
}

/**
 * IntensitySliderCard - Intensity control slider.
 */
@Composable
fun IntensitySliderCard(
    intensity: Float,
    onIntensityChange: (Float) -> Unit
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
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
                onValueChange = onIntensityChange,
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
}

/**
 * QuickTestsCard - Quick preset buttons.
 */
@Composable
fun QuickTestsCard(
    onHappyPath: () -> Unit,
    onThinking: () -> Unit,
    onGenerating: () -> Unit,
    onError: () -> Unit,
    haptics: HapticFeedbackController
) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
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
                    text = "Happy Path",
                    onClick = {
                        onHappyPath()
                        haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonPrimary(
                    text = "Thinking",
                    onClick = {
                        onThinking()
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
                    text = "Generating",
                    onClick = {
                        onGenerating()
                        haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                    },
                    modifier = Modifier.weight(1f)
                )
                MaButtonSecondary(
                    text = "Error",
                    onClick = {
                        onError()
                        haptics.performHapticFeedback(HapticFeedbackType.ERROR)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * DebugInfoRow - A row displaying a label and value.
 */
@Composable
fun DebugInfoRow(label: String, value: String) {
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
