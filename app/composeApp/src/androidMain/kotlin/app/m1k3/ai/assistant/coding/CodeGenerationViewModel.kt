package app.m1k3.ai.assistant.coding

import android.content.Context
import domain.coding.CodingEngine
import domain.coding.CodingModel
import viewmodel.BaseCodeGenerationViewModel

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
 * val viewModel = koinViewModel<CodeGenerationViewModel>()
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
//    override suspend fun createEngine(model: CodingModel): CodingEngine {
//        return when (model) {
////            CodingModel.SMOL_LM2_360M -> SmolLM2CodingEngine(context)
//            CodingModel.SMOL_LM2_360M -> SmolLM2CodingEngine(context)
//            CodingModel.QWEN_CODER_0_5B -> {
//                // TODO: Implement QwenCodingEngine
//                // For now, use SmolLM2 as fallback
//                SmolLM2CodingEngine(context)
//            }
//        }
//    }
    override suspend fun createEngine(model: CodingModel): CodingEngine {
        TODO("Not yet implemented")
    }
}
