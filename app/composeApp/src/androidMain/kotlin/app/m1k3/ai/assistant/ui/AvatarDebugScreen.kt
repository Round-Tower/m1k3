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
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaButtonSecondary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * 間 AI Avatar Debug Screen
 *
 * Comprehensive testing interface for the avatar system:
 * - Toggle between 2D Canvas and 3D Colobus model
 * - Test all 9 emotions with intensity control
 * - Test all 6 activities
 * - Real-time state visualization
 * - Performance metrics
 *
 * Speeds up development and polish by allowing rapid iteration.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarDebugScreen(
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHapticFeedback()

    // Avatar state
    var currentEmotion by remember { mutableStateOf(AvatarEmotion.NEUTRAL) }
    var currentActivity by remember { mutableStateOf(AvatarActivity.IDLE) }
    var intensity by remember { mutableFloatStateOf(0.5f) }
    var use3D by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val avatarState by remember {
        derivedStateOf {
            AvatarState(
                emotion = currentEmotion,
                activity = currentActivity,
                intensity = intensity,
                message = if (use3D) "3D Colobus Monkey" else "2D Canvas Robot"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🎨 Avatar Debug Lab",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary
                        )
                        Text(
                            if (use3D) "Testing 3D Colobus Model" else "Testing 2D Canvas Robot",
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
            MaCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    AvatarView(
                        state = avatarState,
                        showInfo = true,
                        use3D = use3D,
                        modifier = Modifier.size(280.dp)
                    )

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
                            use3D = it
                            haptics.performHapticFeedback(HapticFeedbackType.MEDIUM)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaColors.Orange,
                            checkedTrackColor = MaColors.Orange.copy(alpha = 0.5f)
                        )
                    )
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
                                            currentEmotion = emotion
                                            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = false  // Selected
                                    )
                                } else {
                                    MaButtonSecondary(
                                        text = "${emotion.emoji} ${emotion.displayName}",
                                        onClick = {
                                            currentEmotion = emotion
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
                                            currentActivity = activity
                                            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = false  // Selected
                                    )
                                } else {
                                    MaButtonSecondary(
                                        text = activity.displayName,
                                        onClick = {
                                            currentActivity = activity
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
                        onValueChange = { intensity = it },
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
                onClick = { showAdvanced = !showAdvanced },
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
                                Divider(color = MaColors.BorderLight, modifier = Modifier.padding(vertical = MaSpacing.xs))
                                val animInfo = Avatar3DEngine.getAnimation(avatarState)
                                DebugInfoRow("3D Animation", animInfo.name)
                                DebugInfoRow("Animation Speed", "${Avatar3DEngine.getAnimationSpeed(intensity)}x")
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
                                currentEmotion = AvatarEmotion.HAPPY
                                currentActivity = AvatarActivity.SPEAKING
                                intensity = 0.8f
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MaButtonPrimary(
                            text = "🤔 Thinking",
                            onClick = {
                                currentEmotion = AvatarEmotion.THINKING
                                currentActivity = AvatarActivity.THINKING
                                intensity = 0.6f
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
                                currentEmotion = AvatarEmotion.EXCITED
                                currentActivity = AvatarActivity.GENERATING
                                intensity = 0.9f
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MaButtonSecondary(
                            text = "❌ Error",
                            onClick = {
                                currentEmotion = AvatarEmotion.ANGRY
                                currentActivity = AvatarActivity.ERROR
                                intensity = 1.0f
                                haptics.performHapticFeedback(HapticFeedbackType.ERROR)
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
