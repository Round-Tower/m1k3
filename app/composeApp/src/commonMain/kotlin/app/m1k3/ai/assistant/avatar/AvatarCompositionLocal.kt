package app.m1k3.ai.assistant.avatar

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providers for shared avatar state across all screens.
 *
 * Enables centralized avatar management in MainActivity while allowing
 * all screens to access and update avatar state without prop drilling.
 *
 * Usage:
 * ```kotlin
 * // In MainActivity:
 * val appAvatarVM = rememberAvatarViewModel()
 * CompositionLocalProvider(
 *     LocalSharedAvatarVM provides appAvatarVM
 * ) {
 *     // All screens can now access via CompositionLocal
 * }
 *
 * // In any screen:
 * val avatarVM = LocalSharedAvatarVM.current
 * avatarVM.setEmotion(AvatarEmotion.HAPPY)
 * ```
 */

/**
 * Provides the shared AvatarViewModel for all screens.
 *
 * Use this to:
 * - Update avatar emotion/activity from any screen
 * - Sync with AI generation state
 * - Process messages for emotion detection
 * - Control avatar animations
 *
 * Scope: App-level (MainActivity), persists across screen transitions.
 * Lifecycle: Bound to MainActivity's lifecycle, not individual screen lifecycle.
 */
val LocalSharedAvatarVM =
    staticCompositionLocalOf<AvatarViewModel?> {
        null
    }

/**
 * Provides the current AvatarState for UI rendering.
 *
 * This is a derived state - updates automatically when the ViewModel's
 * internal state changes. Use this in Composables to render avatar.
 *
 * Example:
 * ```kotlin
 * val avatarState by LocalSharedAvatarState.current
 *     ?: return // Handle null case
 *
 * AvatarView(state = avatarState, modifier = Modifier.size(100.dp))
 * ```
 */
val LocalSharedAvatarState =
    staticCompositionLocalOf<AvatarState?> {
        null
    }

/**
 * Provides the currently selected avatar model ID.
 *
 * Wrapped in [androidx.compose.runtime.State] so that Avatar3D renderers
 * recompose when the user picks a new avatar in the gallery.
 *
 * MurphySig: kev+claude / confidence 0.85 / 2026-04-19
 * Rationale: picker writes to prefs AND to this state so any open
 * avatar view swaps models live without a process restart.
 */
val LocalSelectedAvatarId =
    staticCompositionLocalOf<androidx.compose.runtime.State<String>> {
        androidx.compose.runtime.mutableStateOf(ModelRegistry.DEFAULT_MODEL_ID)
    }

/**
 * When the hero splash is on screen the 3D avatar lives THERE; the
 * toolbar hides its own 3D render to avoid two Filament scenes loading
 * the same GLB (that pairing crashes libgltfio-jni.so with a null-ptr
 * deref). Flipped back to true as soon as the user types their first
 * message — toolbar takes ownership of the small avatar from then on.
 *
 * MurphySig: kev+claude / confidence 0.8 / 2026-04-19
 */
val LocalShowToolbarAvatar =
    staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> {
        androidx.compose.runtime.mutableStateOf(true)
    }
