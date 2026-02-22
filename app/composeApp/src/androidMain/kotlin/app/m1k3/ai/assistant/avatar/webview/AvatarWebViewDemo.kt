package app.m1k3.ai.assistant.avatar.webview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarActivity
import app.m1k3.ai.assistant.avatar.AvatarEmotion
import app.m1k3.ai.assistant.avatar.AvatarState

/**
 * M1K3 - WebView Avatar Demo Screen
 *
 * Standalone screen for testing WebView avatar integration.
 * Shows emotion/activity controls and the WebView avatar.
 *
 * **Usage**:
 * ```kotlin
 * // Add to NavHost:
 * composable("avatar-webview-demo") {
 *     AvatarWebViewDemoScreen(
 *         onBackClick = { navController.popBackStack() }
 *     )
 * }
 * ```
 *
 * MurphySig: https://murphysig.dev
 * Confidence: 0.9 - Simple demo screen for testing
 * Context: Phase 1 WebView integration testing
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarWebViewDemoScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Avatar state (local state for demo)
    var avatarState by remember {
        mutableStateOf(
            AvatarState(
                emotion = AvatarEmotion.NEUTRAL,
                activity = AvatarActivity.IDLE,
                intensity = 0.5f
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web Avatar Demo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // WebView Avatar (2/3 of screen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
            ) {
                AvatarWebViewContent(
                    state = avatarState,
                    onModelLoaded = { modelName ->
                        println("Model loaded: $modelName")
                    },
                    enableGitHubExplorer = true,
                    enableShaders = true
                )
            }

            // Controls (1/3 of screen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Emotion Controls", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            avatarState = avatarState.copy(
                                emotion = AvatarEmotion.HAPPY,
                                intensity = 0.9f
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("😊")
                    }
                    Button(
                        onClick = {
                            avatarState = avatarState.copy(
                                emotion = AvatarEmotion.THINKING,
                                intensity = 0.6f
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🤔")
                    }
                    Button(
                        onClick = {
                            avatarState = avatarState.copy(
                                emotion = AvatarEmotion.EXCITED,
                                intensity = 1.0f
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🤩")
                    }
                }

                Text("Activity Controls", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            avatarState = avatarState.copy(activity = AvatarActivity.THINKING)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Think")
                    }
                    Button(
                        onClick = {
                            avatarState = avatarState.copy(activity = AvatarActivity.GENERATING)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Generate")
                    }
                    Button(
                        onClick = {
                            avatarState = avatarState.copy(activity = AvatarActivity.IDLE)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Idle")
                    }
                }

                // Current state display
                Text(
                    text = "Emotion: ${avatarState.emotion.name} | Activity: ${avatarState.activity.name}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
