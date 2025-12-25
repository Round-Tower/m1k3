package viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.m1k3.codingmodule.SmolLM2CodingEngine
import domain.coding.CodingEngine
import domain.coding.CodingModel

/**
 * Android implementation of CodeGenerationViewModel
 *
 * Provides Context-dependent engine creation for Android platform.
 * Currently supports SmolLM2CodingEngine for all models.
 *
 * TODO: Implement QwenCodingEngine for QWEN_CODER_0_5B model
 *
 * Usage:
 * ```kotlin
 * val viewModel = CodeGenerationViewModel(context)
 * ```
 */
class CodeGenerationViewModel(
    private val context: Context
) : BaseCodeGenerationViewModel() {

    /**
     * Create engine for specified model (Android implementation)
     *
     * Creates the appropriate CodingEngine implementation:
     * - SMOL_LM2_360M → SmolLM2CodingEngine (general-purpose)
     * - QWEN_CODER_0_5B → SmolLM2CodingEngine (fallback, Qwen not implemented yet)
     */
    override suspend fun createEngine(model: CodingModel): CodingEngine {
        return when (model) {
            CodingModel.SMOL_LM2_360M -> SmolLM2CodingEngine(context)
            CodingModel.QWEN_CODER_0_5B -> {
                // TODO: Implement QwenCodingEngine
                // For now, use SmolLM2 as fallback
                SmolLM2CodingEngine(context)
            }
        }
    }
}
