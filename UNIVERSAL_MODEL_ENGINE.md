# Universal Model Engine

## Overview

The Universal Model Engine is a model-agnostic AI inference system that automatically detects model metadata and uses appropriate prompt templates for optimal responses. This replaces the hardcoded `smollm_engine.py` with a dynamic system that works with any Ollama model.

## Problem Solved

**Before:** The system used a hardcoded SmolLM engine that forced ChatML prompt format (`<|im_start|>` and `<|im_end|>` tokens) on all models, including Gemma3, which caused:
- Malformed prompts for non-ChatML models
- Poor responses like "I am not sure" from Gemma3
- Duplicate user messages and context pollution
- Incorrect context lengths and parameters

**After:** Dynamic model detection and format-specific prompting ensures each model uses its native template and capabilities.

## Architecture

### Core Components

1. **`universal_model_engine.py`** - Main engine class
2. **Model Metadata Detection** - Automatic detection via Ollama API
3. **Template Management** - Dynamic prompt formatting per model
4. **Streaming Support** - Real-time response generation
5. **Context Management** - Model-appropriate context handling

### Model Detection Process

```python
# 1. Query Ollama metadata API
curl http://localhost:11434/api/show -d '{"name": "gemma3:270m"}'

# 2. Extract key information:
#   - family: "gemma3" vs "llama" (SmolLM)  
#   - template: "<start_of_turn>" vs "<|im_start|>"
#   - context_length: 32768 vs 8192
#   - stop_tokens: ["<end_of_turn>"] vs ["<|im_end|>"]
#   - parameters: top_k, top_p, etc.

# 3. Use model-specific formatting automatically
```

### Supported Models

#### Gemma3 Models
- **Template:** `<start_of_turn>user\n{content}<end_of_turn>\n<start_of_turn>model\n`
- **Context:** 32,768 tokens
- **Stop:** `["<end_of_turn>"]`
- **Family:** `gemma3`

#### SmolLM Models  
- **Template:** `<|im_start|>user\n{content}<|im_end|>\n<|im_start|>assistant\n`
- **Context:** 8,192 tokens
- **Stop:** `["<|im_end|>"]`
- **Family:** `llama`

#### Future Models
- Automatically detected and configured
- No code changes required for new models

## Usage

### Basic Usage

```python
from src.engines.ai.universal_model_engine import UniversalModelEngine

# Create engine for any model
engine = UniversalModelEngine("gemma3:270m")

# Load with automatic metadata detection
if engine.load_model():
    # Generate response with proper formatting
    response = engine.generate_response(
        "What is the capital of France?",
        system_prompt="You are a helpful assistant."
    )
    print(response)  # "The capital of France is Paris."
```

### Streaming Responses

```python
# Stream tokens in real-time
for token in engine.generate_streaming_response(
    "Explain quantum computing",
    max_tokens=500
):
    print(token, end="", flush=True)
```

### Model Switching

```python
# Switch models dynamically
engine.switch_model("smollm2:135m")  
# Automatically reconfigures for SmolLM's ChatML format
```

## Integration with M1K3 CLI

The Universal Engine integrates seamlessly with the existing CLI:

```python
# In ai_inference.py
class LocalAIEngine:
    def __init__(self):
        # Priority 1: Universal Engine (preferred)
        if UNIVERSAL_ENGINE_AVAILABLE:
            self.universal_engine = UniversalModelEngine("gemma3:270m")
        
        # Priority 2: Legacy SmolLM engine (fallback)
        elif SMOLLM_ENGINE_AVAILABLE:
            self.smollm_engine = SmolLMEngine("smollm_config.json")
```

## Configuration

### Model Profiles (Optional)

While the Universal Engine works without configuration, you can enhance it with model profiles:

```json
{
  "model_profiles": {
    "instruction_tuned": {
      "description": "Instruction-following models",
      "optimal_format": "chatml",
      "context_strategy": "structured",
      "max_system_prompt_length": 1000,
      "models": ["gemma*", "llama3*"]
    }
  }
}
```

### Auto-Detection Rules

```json
{
  "auto_detection": {
    "rules": [
      {
        "pattern": "gemma*",
        "profile": "instruction_tuned", 
        "confidence": 0.95
      }
    ]
  }
}
```

## API Reference

### UniversalModelEngine Class

#### Methods

- `load_model(model_name)` - Load and configure model
- `generate_response(prompt, system_prompt, max_tokens, temperature)` - Generate single response
- `generate_streaming_response(prompt, ...)` - Generate streaming response
- `switch_model(new_model)` - Switch to different model
- `clear_context()` - Clear conversation history
- `get_model_info()` - Get current model metadata

#### Properties

- `current_model_name` - Currently loaded model
- `model_metadata` - Detected model metadata
- `conversation_history` - Message history
- `max_context_length` - Model context limit

## Benefits

### Performance
- **Optimal Context Usage:** Each model uses its native context length
- **Proper Parameters:** Model-specific top_k, top_p, temperature
- **Efficient Templates:** Native prompt formats reduce token waste

### Reliability  
- **Better Responses:** Proper formatting eliminates "I am not sure" responses
- **Error Handling:** Robust fallback to legacy engines
- **Validation:** Automatic model availability testing

### Maintainability
- **Future-Proof:** New models work automatically
- **Single Codebase:** One engine handles all models  
- **Clean Architecture:** Separation of concerns

## Testing

### Test Results

#### Gemma3 (Before Universal Engine)
```
Q: What is the capital of France?
A: I am not sure  # ❌ Due to malformed ChatML prompts
```

#### Gemma3 (After Universal Engine)  
```
Q: What is the capital of France?
A: The capital of France is Paris.  # ✅ Proper response

Q: What is 2 + 2?
A: 2 + 2 = 4  # ✅ Correct formatting

Q: What color is the sky?
A: The sky is blue.  # ✅ Working perfectly
```

### Performance Testing

- **SmolLM:** Works with ChatML format as before
- **Gemma3:** Now provides accurate, helpful responses
- **Context Management:** Proper 32K context for Gemma3 vs 8K for SmolLM
- **Streaming:** Real-time token generation for both models

## Deployment

The Universal Engine is now the default in M1K3:

1. **Automatic:** Loads automatically with CLI startup
2. **Transparent:** Existing code works without changes  
3. **Fallback:** Legacy engines available if needed
4. **Production Ready:** Full error handling and logging

## Future Enhancements

### Planned Features
- **HuggingFace Integration:** Direct model loading from HF Hub
- **Custom Templates:** User-defined prompt formats
- **Multi-Model:** Parallel inference across models
- **Caching:** Template and metadata caching
- **Metrics:** Performance monitoring and analytics

### Extensibility
The Universal Engine is designed for easy extension:

```python
class UniversalModelEngine:
    def _detect_custom_metadata(self, model_name):
        # Add custom model detection logic
        pass
    
    def _format_custom_template(self, messages):
        # Add custom template formatting
        pass
```

## Conclusion

The Universal Model Engine represents a significant architectural improvement that:

- ✅ **Fixes Gemma3 response quality issues**
- ✅ **Provides model-agnostic architecture** 
- ✅ **Maintains backward compatibility**
- ✅ **Enables easy model addition**
- ✅ **Improves maintainability and performance**

This foundation enables M1K3 to work optimally with any current or future language model.