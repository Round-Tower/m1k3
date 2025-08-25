# M1K3 Engine Architecture

This directory contains the AI inference and voice synthesis engines that power M1K3's capabilities.

## Multi-Backend Strategy

M1K3 implements a multi-tiered backend system with automatic fallback:

1. **Primary**: HuggingFace Transformers (TinyLlama-1.1B-Chat)
2. **Secondary**: ctransformers (GGUF quantized models) 
3. **Fallback**: SimpleAIEngine (enhanced mock with 8K context)

## AI Engines (`ai/`)

- **ai_inference.py**: Main AI inference coordination layer
- **adaptive_ai_engine.py**: Dynamic model selection based on system resources
- **simple_ai_engine.py**: Fallback engine with guaranteed compatibility
- **smollm_engine.py**: SmolLM model integration for efficient inference
- **local_model_manager.py**: Model lifecycle management
- **model_transparency.py**: Model debugging and introspection

## Voice Engines (`voice/`)

- **voice_engine.py**: Core voice synthesis interface
- **unified_voice_engine.py**: Multi-provider voice synthesis coordinator
- **turbo_voice_engine.py**: Optimized low-latency voice processing
- **retro_voice_engine.py**: Nostalgic voice effects and styling
- **multi_tier_voice_engine.py**: Quality-based voice tier selection
- **hybrid_voice_engine.py**: Combines multiple synthesis approaches

## Design Decisions

### Hot-Swappable Engines
Engines can be switched at runtime without losing conversation context, enabling dynamic optimization based on system resources.

### Universal Compatibility
The fallback chain ensures M1K3 works on any platform (x86_64, ARM64, various Python versions) by gracefully degrading to simpler implementations.

### Performance Optimization
- Lazy loading prevents unnecessary resource consumption
- Model preloading for frequently used engines
- Memory-efficient inference with quantization support

### Extension Points
- Plugin-based architecture for adding new AI providers
- Configurable quality vs. speed trade-offs
- Custom model integration support