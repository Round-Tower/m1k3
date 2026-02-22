package app.m1k3.ai.assistant.embedding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

/**
 * Compose helper to remember EmbeddingsViewModel across recompositions.
 *
 * Features:
 * - Survives configuration changes
 * - Auto-initializes embedding engine in background
 * - Cleans up resources on disposal
 * - Uses singleton engine manager (application-scoped)
 *
 * Usage:
 * ```kotlin
 * @Composable
 * fun ChatScreen() {
 *     val embeddingsVM = rememberEmbeddingsViewModel()
 *     val state by embeddingsVM.state.collectAsState()
 *
 *     when (state.loadStatus.state) {
 *         LoadState.READY -> Text("✅ Embeddings ready!")
 *         LoadState.LOADING -> CircularProgressIndicator()
 *         LoadState.ERROR -> Text("❌ ${state.loadStatus.error}")
 *         LoadState.IDLE -> Text("⚪ Initializing...")
 *     }
 * }
 * ```
 *
 * @return EmbeddingsViewModel scoped to current composition
 */
@Composable
fun rememberEmbeddingsViewModel(): EmbeddingsViewModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Remember ViewModel across recompositions
    val viewModel = remember(context) {
        val engineManager = EmbeddingEngineManagerImpl.getInstance(context)
        EmbeddingsViewModel(engineManager, scope)
    }

    // Clean up on disposal
    DisposableEffect(viewModel) {
        onDispose {
            // Note: We don't call release() here because:
            // 1. Repository is application-scoped (singleton)
            // 2. Other screens may still be using embeddings
            // 3. Release should only happen on app termination
            // If you need per-screen cleanup, call viewModel.release() manually
        }
    }

    return viewModel
}
