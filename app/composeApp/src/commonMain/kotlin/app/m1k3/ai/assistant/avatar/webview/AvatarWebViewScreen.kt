package app.m1k3.ai.assistant.avatar.webview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarEmotion
import app.m1k3.ai.assistant.avatar.AvatarActivity
import app.m1k3.ai.assistant.avatar.AvatarState
import app.m1k3.ai.assistant.avatar.AvatarViewModel
import app.m1k3.ai.assistant.avatar.collectAsState
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 間 AI - 3D Avatar WebView Screen
 *
 * Proof of concept for Three.js-based 3D avatar rendering in WebView.
 *
 * Features:
 * - Three.js r128 WebGL rendering
 * - GLB model loading with skeletal animations
 * - Morph target (shape key) support for eye animations
 * - JavaScript ↔ Kotlin bridge communication
 * - FPS monitoring
 * - Emotion and activity state synchronization
 *
 * Architecture:
 * ```
 * AvatarViewModel (Kotlin)
 *        ↓ (state updates)
 * AvatarWebViewScreen
 *        ↓ (evaluateJavaScript)
 * index.html (Three.js)
 *        ↓ (render 3D scene)
 * WebGL Canvas
 * ```
 *
 * Performance Targets:
 * - High-end: 45-60 FPS
 * - Mid-range: 30-40 FPS
 * - Low-end: 20-30 FPS
 */

/**
 * Avatar state data for JavaScript bridge
 */
@Serializable
data class AvatarStateJS(
    val emotion: String,
    val activity: String,
    val intensity: Float,
    val message: String? = null
)

/**
 * 3D Animal Model Data
 */
data class AnimalModel(
    val name: String,
    val fileName: String,
    val emoji: String
) {
    companion object {
        val ALL_ANIMALS = listOf(
            AnimalModel("Colobus", "Colobus_Complete.glb", "🐵"),
            AnimalModel("Gecko", "Gecko_Complete.glb", "🦎"),
            AnimalModel("Herring", "Herring_Complete.glb", "🐟"),
            AnimalModel("Inkfish", "Inkfish_Complete.glb", "🦑"),
            AnimalModel("Muskrat", "Muskrat_Complete.glb", "🦦"),
            AnimalModel("Pudu", "Pudu_Complete.glb", "🦌"),
            AnimalModel("Sparrow", "Sparrow_Complete.glb", "🐦"),
            AnimalModel("Taipan", "Taipan_Complete.glb", "🐍")
        )
    }
}

/**
 * 3D Avatar WebView Screen
 *
 * @param avatarViewModel Avatar state manager
 * @param onBackClick Navigation callback
 * @param modifier Compose modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
expect fun AvatarWebViewScreen(
    avatarViewModel: AvatarViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
)

/**
 * Shared UI controls for WebView demo
 */
@Composable
fun WebViewDemoControls(
    avatarViewModel: AvatarViewModel,
    onLoadModel: (AnimalModel) -> Unit,
    currentFPS: Int,
    currentModelName: String,
    modifier: Modifier = Modifier
) {
    val avatarState by avatarViewModel.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // FPS Display
        MaCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Performance",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextPrimary
                    )
                    Text(
                        text = "WebGL rendering speed",
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                }
                Text(
                    text = "$currentFPS FPS",
                    style = MaTypography.headlineSmall,
                    color = when {
                        currentFPS >= 45 -> MaColors.Orange
                        currentFPS >= 30 -> MaColors.TextPrimary
                        else -> MaColors.Error
                    }
                )
            }
        }

        // Current Model Display
        MaCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Model",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextPrimary
                    )
                    Text(
                        text = "29 morphs + 18 animations",
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                }
                Text(
                    text = currentModelName,
                    style = MaTypography.headlineSmall,
                    color = MaColors.Orange
                )
            }
        }

        // Emotion Controls
        MaCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Emotion Controls",
                    style = MaTypography.bodyMedium,
                    color = MaColors.TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            avatarViewModel.setEmotion(AvatarEmotion.HAPPY, 0.9f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.emotion == AvatarEmotion.HAPPY)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Happy")
                    }

                    Button(
                        onClick = {
                            avatarViewModel.setEmotion(AvatarEmotion.SAD, 0.7f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.emotion == AvatarEmotion.SAD)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Sad")
                    }

                    Button(
                        onClick = {
                            avatarViewModel.setEmotion(AvatarEmotion.EXCITED, 1.0f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.emotion == AvatarEmotion.EXCITED)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Excited")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            avatarViewModel.setEmotion(AvatarEmotion.ANGRY, 0.8f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.emotion == AvatarEmotion.ANGRY)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Angry")
                    }

                    Button(
                        onClick = {
                            avatarViewModel.setEmotion(AvatarEmotion.THINKING, 0.6f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.emotion == AvatarEmotion.THINKING)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Thinking")
                    }

                    Button(
                        onClick = {
                            avatarViewModel.setEmotion(AvatarEmotion.LOVE, 0.9f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.emotion == AvatarEmotion.LOVE)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Love")
                    }
                }
            }
        }

        // Activity Controls
        MaCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Activity Controls",
                    style = MaTypography.bodyMedium,
                    color = MaColors.TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { avatarViewModel.startThinking() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.activity == AvatarActivity.THINKING)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Thinking")
                    }

                    Button(
                        onClick = { avatarViewModel.startGenerating() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.activity == AvatarActivity.GENERATING)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Generating")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { avatarViewModel.startSpeaking() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.activity == AvatarActivity.SPEAKING)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Speaking")
                    }

                    Button(
                        onClick = { avatarViewModel.returnToIdle() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (avatarState.activity == AvatarActivity.IDLE)
                                MaColors.Orange else MaColors.BgElevated
                        )
                    ) {
                        Text("Idle")
                    }
                }
            }
        }

        // Model Selection
        MaCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select Animal Model",
                    style = MaTypography.bodyMedium,
                    color = MaColors.TextPrimary
                )

                // First row: Colobus, Gecko, Herring, Inkfish
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimalModel.ALL_ANIMALS.take(4).forEach { animal ->
                        Button(
                            onClick = { onLoadModel(animal) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentModelName.contains(animal.name))
                                    MaColors.Orange else MaColors.BgElevated
                            )
                        ) {
                            Text("${animal.emoji}")
                        }
                    }
                }

                // Second row: Muskrat, Pudu, Sparrow, Taipan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimalModel.ALL_ANIMALS.drop(4).forEach { animal ->
                        Button(
                            onClick = { onLoadModel(animal) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentModelName.contains(animal.name))
                                    MaColors.Orange else MaColors.BgElevated
                            )
                        ) {
                            Text("${animal.emoji}")
                        }
                    }
                }
            }
        }

        // Current State Display
        MaCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Current State",
                    style = MaTypography.bodyMedium,
                    color = MaColors.TextPrimary
                )
                Text(
                    text = "Emotion: ${avatarState.emotion.name}",
                    style = MaTypography.bodySmall,
                    color = MaColors.TextSecondary
                )
                Text(
                    text = "Activity: ${avatarState.activity.name}",
                    style = MaTypography.bodySmall,
                    color = MaColors.TextSecondary
                )
                Text(
                    text = "Intensity: ${(avatarState.intensity * 100).toInt()}%",
                    style = MaTypography.bodySmall,
                    color = MaColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Convert AvatarState to JavaScript-compatible JSON
 */
fun AvatarState.toJS(): String {
    val jsState = AvatarStateJS(
        emotion = emotion.name.lowercase(),
        activity = activity.name,
        intensity = intensity,
        message = message
    )
    return Json.encodeToString(jsState)
}
