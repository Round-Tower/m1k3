package app.m1k3.ai.assistant.avatar.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.avatar.InteractionType
import app.m1k3.ai.assistant.avatar.PetViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 間 AI Pet Interaction Overlay
 *
 * Transparent touch layer that captures user interactions with the pixel pet.
 * Recognizes gestures and triggers appropriate pet responses with visual feedback.
 *
 * **Gesture Recognition:**
 * - Single tap: Pat (+5 happiness, +2 energy) - "Pet enjoyed the pat! 💙"
 * - Double tap: Energize (+10 happiness, +5 energy) - "Extra love! 💕"
 * - Long press: Deep care (+15 happiness, +10 energy) - "Deep connection! ✨"
 *
 * **Visual Feedback:**
 * - Toast message showing interaction result
 * - Heart particle spawned at tap location
 * - Stat bars animate upward
 *
 * **Philosophy:**
 * Physical touch creates emotional bond. Direct interaction nurtures
 * the pixel pet beyond passive eco credit accumulation.
 */

@Composable
fun PetInteractionOverlay(
    petViewModel: PetViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showFeedback: Boolean = true
) {
    var interactionMessage by remember { mutableStateOf<String?>(null) }
    var interactionPosition by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectTapGestures(
                    onTap = { offset ->
                        // Single tap: Pat
                        interactionPosition = offset
                        petViewModel.onPat(InteractionType.PAT)

                        if (showFeedback) {
                            interactionMessage = InteractionType.PAT.displayMessage
                            scope.launch {
                                delay(2000)
                                interactionMessage = null
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        // Double tap: Extra love
                        interactionPosition = offset
                        petViewModel.onPat(InteractionType.DOUBLE_TAP)

                        if (showFeedback) {
                            interactionMessage = InteractionType.DOUBLE_TAP.displayMessage
                            scope.launch {
                                delay(2000)
                                interactionMessage = null
                            }
                        }
                    },
                    onLongPress = { offset ->
                        // Long press: Deep connection
                        interactionPosition = offset
                        petViewModel.onPat(InteractionType.LONG_PRESS)

                        if (showFeedback) {
                            interactionMessage = InteractionType.LONG_PRESS.displayMessage
                            scope.launch {
                                delay(2000)
                                interactionMessage = null
                            }
                        }
                    }
                )
            }
    ) {
        // Show interaction feedback message
        if (interactionMessage != null) {
            InteractionToast(
                message = interactionMessage!!,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Interaction feedback toast
 */
@Composable
private fun InteractionToast(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp
    )
}

/**
 * Swipe gesture detector (for future interactions)
 */
@Composable
fun PetSwipeInteraction(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var startOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        startOffset = offset
                    }
                )
            }
    ) {
        content()
    }
}

/**
 * Feed button overlay (optional explicit feed action)
 */
@Composable
fun PetFeedButton(
    petViewModel: PetViewModel,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // TODO: Implement explicit feed button UI
    // This would be a floating action button that triggers FEED interaction
    // Can be used when user completes a task or achievement
}

/**
 * Usage Examples:
 * ```kotlin
 * // Basic interaction overlay
 * Box {
 *     // Pet rendering
 *     Canvas(Modifier.fillMaxSize()) {
 *         with(AvatarEngine) {
 *             drawPixelPet(petState, avatarState)
 *         }
 *     }
 *
 *     // Interaction layer on top
 *     PetInteractionOverlay(
 *         petViewModel = petViewModel,
 *         enabled = true,
 *         showFeedback = true
 *     )
 * }
 *
 * // Disabled during AI processing
 * PetInteractionOverlay(
 *     petViewModel = petViewModel,
 *     enabled = !isProcessing
 * )
 *
 * // Without visual feedback (silent mode)
 * PetInteractionOverlay(
 *     petViewModel = petViewModel,
 *     showFeedback = false
 * )
 * ```
 */
