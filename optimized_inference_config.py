#!/usr/bin/env python3
"""
Optimized LLM Configuration for M1K3
Reduces hallucinations and improves response quality
"""

def get_anti_hallucination_params(model_name: str, max_tokens: int = 150) -> dict:
    """
    Get optimized parameters designed to reduce hallucinations
    Focus on accuracy, consistency, and factual responses
    """
    
    # Base parameters optimized for accuracy (only supported params)
    base_params = {
        'pad_token_id': None,  # Will be set from tokenizer
        'eos_token_id': None,  # Will be set from tokenizer
        'do_sample': True,
    }
    
    model_lower = model_name.lower()
    
    if 'qwen3-0.6b' in model_lower or 'qwen/qwen3-0.6b' in model_lower:
        # Qwen3-0.6B: Use greedy decoding to avoid thinking mode and hallucinations
        return {
            **base_params,
            'max_new_tokens': min(max_tokens, 30),      # Very short responses
            'do_sample': False,                         # Greedy decoding - most likely tokens only
            'repetition_penalty': 1.1,                 # Light repetition penalty
            'no_repeat_ngram_size': 2,                 # Prevent short repetitions
        }
    
    elif 'gemma' in model_lower:
        # Gemma models: Instruction-tuned, optimize for following directions
        return {
            **base_params,
            'max_new_tokens': min(max_tokens, 100),
            'temperature': 0.4,                         # Low temp for instruction following
            'top_p': 0.8,
            'top_k': 25,
            'repetition_penalty': 1.1,
            'no_repeat_ngram_size': 3,
            'num_beams': 1,
        }
    
    elif 'tinyllama' in model_lower:
        # TinyLlama: Small model, need to be very conservative
        return {
            **base_params,
            'max_new_tokens': min(max_tokens, 80),
            'temperature': 0.5,                         # Conservative temperature
            'top_p': 0.85,
            'top_k': 30,
            'repetition_penalty': 1.2,
            'no_repeat_ngram_size': 3,
            'num_beams': 1,
        }
    
    elif 'phi-3' in model_lower:
        # Phi-3: Reasoning model, optimize for logical consistency
        return {
            **base_params,
            'max_new_tokens': min(max_tokens, 150),
            'temperature': 0.2,                         # Very low for reasoning
            'top_p': 0.6,                              # Conservative sampling
            'top_k': 15,                               # Very focused
            'repetition_penalty': 1.1,
            'no_repeat_ngram_size': 4,
            'num_beams': 1,
        }
    
    elif 'dialogpt' in model_lower:
        # DialoGPT: Conversational, but needs constraint
        return {
            **base_params,
            'max_new_tokens': min(max_tokens, 60),      # Very short for chat
            'temperature': 0.6,
            'top_p': 0.9,
            'top_k': 40,
            'repetition_penalty': 1.3,
            'no_repeat_ngram_size': 4,
            'num_beams': 1,
        }
    
    else:
        # Conservative defaults for unknown models
        return {
            **base_params,
            'max_new_tokens': min(max_tokens, 80),
            'temperature': 0.5,
            'top_p': 0.8,
            'top_k': 30,
            'repetition_penalty': 1.15,
            'no_repeat_ngram_size': 3,
            'num_beams': 1,
        }

def get_optimized_prompt_format(model_name: str, prompt: str, context_history: list = None) -> str:
    """
    Get optimized prompt formatting to reduce hallucinations
    """
    model_lower = model_name.lower()
    
    # Clear, direct instruction format
    if 'qwen3' in model_lower:
        # Qwen3 with thinking mode suppression
        system_msg = "You are a helpful AI assistant. Give direct, concise answers only. Do not show your thinking process. Do not use <think> tags. Answer in one short sentence."
        
        # Use simple format to avoid triggering thinking mode
        return f"User: {prompt}\n\nAssistant:"
    
    elif 'gemma' in model_lower:
        # Gemma format with clear instructions
        instruction = "Answer this question directly and concisely:"
        return f"<start_of_turn>user\n{instruction} {prompt}<end_of_turn>\n<start_of_turn>model\n"
    
    elif 'phi-3' in model_lower:
        # Phi-3 format with reasoning constraint
        system_msg = "You are a precise AI assistant. Give accurate, direct answers without speculation."
        return f"<|system|>{system_msg}<|end|>\n<|user|>{prompt}<|end|>\n<|assistant|>"
    
    else:
        # Conservative default format
        return f"Human: {prompt}\n\nAssistant: "

def get_response_filters() -> list:
    """
    Get post-processing filters to clean up responses
    """
    return [
        # Remove common hallucination patterns
        ("I'm sorry, but I don't have access to", ""),
        ("Based on my training data", ""),
        ("As an AI language model", ""),
        ("I don't have real-time information", ""),
        
        # Remove reference to previous conversations that don't exist
        ("in our previous conversation", ""),
        ("as we discussed earlier", ""),
        ("referring back to what you said", ""),
        
        # Remove uncertainty hedging (over-hedging leads to confusion)
        ("I think that maybe", ""),
        ("it's possible that perhaps", ""),
        ("I believe it might be", ""),
        
        # Clean up repetitive phrases
        ("Let me think about this", ""),
        ("That's a great question", ""),
        ("I understand what you're asking", ""),
    ]

def validate_response_quality(response: str, original_prompt: str) -> tuple[bool, str]:
    """
    Validate response quality and detect potential hallucinations
    Returns (is_valid, cleaned_response)
    """
    
    # Basic quality checks
    if len(response.strip()) < 3:
        return False, "I need more information to provide a helpful answer."
    
    # Check for obvious hallucinations and thinking mode artifacts
    hallucination_indicators = [
        "let me check again",
        "wait, maybe there was a typo", 
        "the user wrote",
        "looking at the history",
        "perhaps it's supposed to be",
        "alternatively, maybe",
        "or maybe i misread",
        "<think>",
        "</think>",
        "okay, let me",
        "let's see",
        "wait, hold on",
        "let me recall",
        "first, i need to",
        "wait, but in my knowledge",
        "however, since i can't",
    ]
    
    response_lower = response.lower()
    hallucination_count = 0
    for indicator in hallucination_indicators:
        if indicator in response_lower:
            hallucination_count += 1
    
    # Only trigger if multiple indicators or severe ones
    if hallucination_count >= 2 or any(severe in response_lower for severe in ['<think>', 'wait, hold on', 'let me check again']):
        # This response shows significant confusion/hallucination
        return False, "I'll focus on your specific question. Could you please repeat it?"
    
    # Check for excessive rambling (potential hallucination)
    sentences = response.split('.')
    if len(sentences) > 5 and any(len(s.split()) > 30 for s in sentences):
        # Too verbose, likely rambling
        first_sentence = sentences[0].strip()
        if len(first_sentence) > 10:
            return True, first_sentence + "."
        return False, "Let me give you a more direct answer."
    
    # Apply response filters
    cleaned_response = response
    for pattern, replacement in get_response_filters():
        cleaned_response = cleaned_response.replace(pattern, replacement)
    
    # Remove thinking mode content specifically
    import re
    # Remove <think>...</think> blocks
    cleaned_response = re.sub(r'<think>.*?</think>', '', cleaned_response, flags=re.DOTALL)
    # Remove thinking mode artifacts at start
    if cleaned_response.lower().startswith(('okay,', 'alright,', 'let me', 'let\'s see')):
        # Find the actual answer after the thinking
        sentences = cleaned_response.split('.')
        for i, sentence in enumerate(sentences[1:], 1):
            if len(sentence.strip()) > 5 and not any(indicator in sentence.lower() for indicator in ['let me', 'okay', 'wait']):
                cleaned_response = '.'.join(sentences[i:])
                break
    
    return True, cleaned_response.strip()