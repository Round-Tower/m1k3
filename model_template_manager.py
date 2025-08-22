#!/usr/bin/env python3
"""
M1K3 Model Template Manager
Universal chat template system supporting diverse model formats
"""

from typing import List, Dict, Optional, Tuple, Any
from dataclasses import dataclass
from enum import Enum
import re


class TemplateType(Enum):
    CHATML = "chatml"          # ChatML format: <|im_start|>role<|im_end|>
    ALPACA = "alpaca"          # Alpaca format: ### Instruction/Response
    VICUNA = "vicuna"          # Vicuna format: USER:/ASSISTANT:
    LLAMA2 = "llama2"          # Llama-2 format: [INST] [/INST]
    MISTRAL = "mistral"        # Mistral format: <s>[INST] [/INST]
    QWEN = "qwen"              # Qwen format: <|im_start|> (ChatML variant)
    GENERIC = "generic"        # Fallback format: Human:/Assistant:


@dataclass
class ChatTemplate:
    """Chat template specification"""
    name: str
    system_support: bool
    user_prefix: str
    user_suffix: str
    assistant_prefix: str
    assistant_suffix: str
    system_prefix: str = ""
    system_suffix: str = ""
    bos_token: str = ""
    eos_token: str = ""
    generation_prompt: str = ""


class ModelTemplateManager:
    """Manages chat templates for different model architectures"""
    
    def __init__(self):
        self.templates = self._initialize_templates()
        self.model_mappings = self._initialize_model_mappings()
    
    def _initialize_templates(self) -> Dict[TemplateType, ChatTemplate]:
        """Initialize all supported chat templates"""
        return {
            TemplateType.CHATML: ChatTemplate(
                name="ChatML",
                system_support=True,
                system_prefix="<|im_start|>system\n",
                system_suffix="<|im_end|>\n",
                user_prefix="<|im_start|>user\n", 
                user_suffix="<|im_end|>\n",
                assistant_prefix="<|im_start|>assistant\n",
                assistant_suffix="<|im_end|>\n",
                generation_prompt="<|im_start|>assistant\n"
            ),
            
            TemplateType.QWEN: ChatTemplate(
                name="Qwen",
                system_support=True,
                system_prefix="<|im_start|>system\n",
                system_suffix="<|im_end|>\n",
                user_prefix="<|im_start|>user\n",
                user_suffix="<|im_end|>\n", 
                assistant_prefix="<|im_start|>assistant\n",
                assistant_suffix="<|im_end|>\n",
                generation_prompt="<|im_start|>assistant\n"
            ),
            
            TemplateType.ALPACA: ChatTemplate(
                name="Alpaca",
                system_support=False,
                user_prefix="### Instruction:\n",
                user_suffix="\n\n",
                assistant_prefix="### Response:\n",
                assistant_suffix="\n\n",
                generation_prompt="### Response:\n"
            ),
            
            TemplateType.VICUNA: ChatTemplate(
                name="Vicuna",
                system_support=True,
                system_prefix="SYSTEM: ",
                system_suffix="\n",
                user_prefix="USER: ",
                user_suffix="\n",
                assistant_prefix="ASSISTANT: ",
                assistant_suffix="\n",
                generation_prompt="ASSISTANT: "
            ),
            
            TemplateType.LLAMA2: ChatTemplate(
                name="Llama-2",
                system_support=True,
                system_prefix="<<SYS>>\n",
                system_suffix="\n<</SYS>>\n\n",
                user_prefix="[INST] ",
                user_suffix=" [/INST] ",
                assistant_prefix="",
                assistant_suffix=" ",
                bos_token="<s>",
                eos_token="</s>",
                generation_prompt=""
            ),
            
            TemplateType.MISTRAL: ChatTemplate(
                name="Mistral",
                system_support=False,
                user_prefix="[INST] ",
                user_suffix=" [/INST] ",
                assistant_prefix="",
                assistant_suffix="</s> ",
                bos_token="<s>",
                eos_token="</s>",
                generation_prompt=""
            ),
            
            TemplateType.GENERIC: ChatTemplate(
                name="Generic",
                system_support=True,
                system_prefix="System: ",
                system_suffix="\n\n",
                user_prefix="Human: ",
                user_suffix="\n\n",
                assistant_prefix="Assistant: ",
                assistant_suffix="\n\n",
                generation_prompt="Assistant: "
            )
        }
    
    def _initialize_model_mappings(self) -> Dict[str, TemplateType]:
        """Map model names to their preferred templates"""
        return {
            # Qwen models
            "qwen": TemplateType.QWEN,
            "qwen2": TemplateType.QWEN,
            "qwen3": TemplateType.QWEN,
            
            # ChatML models
            "openchat": TemplateType.CHATML,
            "starling": TemplateType.CHATML,
            "neural-chat": TemplateType.CHATML,
            
            # Alpaca models
            "alpaca": TemplateType.ALPACA,
            "stanford-alpaca": TemplateType.ALPACA,
            
            # Vicuna models  
            "vicuna": TemplateType.VICUNA,
            "fastchat": TemplateType.VICUNA,
            
            # Llama models
            "llama-2": TemplateType.LLAMA2,
            "llama2": TemplateType.LLAMA2,
            "code-llama": TemplateType.LLAMA2,
            
            # Mistral models
            "mistral": TemplateType.MISTRAL,
            "mixtral": TemplateType.MISTRAL,
            
            # TinyLlama (often uses Alpaca)
            "tinyllama": TemplateType.ALPACA,
            
            # DialoGPT (generic format works best)
            "dialogpt": TemplateType.GENERIC,
            "distilgpt2": TemplateType.GENERIC,
            "gpt2": TemplateType.GENERIC,
        }
    
    def detect_template_from_tokenizer(self, tokenizer) -> TemplateType:
        """Detect template type from tokenizer chat template"""
        if not hasattr(tokenizer, 'chat_template') or not tokenizer.chat_template:
            return TemplateType.GENERIC
        
        chat_template = tokenizer.chat_template.lower()
        
        # Check for specific template patterns
        if '<|im_start|>' in chat_template:
            return TemplateType.CHATML if 'qwen' not in chat_template else TemplateType.QWEN
        elif '[inst]' in chat_template and '[/inst]' in chat_template:
            return TemplateType.LLAMA2 if '<<sys>>' in chat_template else TemplateType.MISTRAL
        elif '### instruction' in chat_template:
            return TemplateType.ALPACA
        elif 'user:' in chat_template and 'assistant:' in chat_template:
            return TemplateType.VICUNA
        
        return TemplateType.GENERIC
    
    def detect_template_from_model_name(self, model_name: str) -> TemplateType:
        """Detect template type from model name"""
        model_name_lower = model_name.lower()
        
        for pattern, template_type in self.model_mappings.items():
            if pattern in model_name_lower:
                return template_type
        
        return TemplateType.GENERIC
    
    def get_template(self, model_name: str = None, tokenizer = None) -> ChatTemplate:
        """Get the appropriate chat template for a model"""
        
        # First try tokenizer detection (most accurate)
        if tokenizer:
            template_type = self.detect_template_from_tokenizer(tokenizer)
            if template_type != TemplateType.GENERIC:
                return self.templates[template_type]
        
        # Fall back to model name detection
        if model_name:
            template_type = self.detect_template_from_model_name(model_name)
            return self.templates[template_type]
        
        # Final fallback
        return self.templates[TemplateType.GENERIC]
    
    def format_conversation(self, messages: List[Dict[str, str]], 
                          template: ChatTemplate,
                          add_generation_prompt: bool = True) -> str:
        """Format a conversation using the specified template"""
        
        formatted_parts = []
        
        # Add BOS token if needed
        if template.bos_token:
            formatted_parts.append(template.bos_token)
        
        for message in messages:
            role = message.get("role", "user")
            content = message.get("content", "")
            
            if role == "system" and template.system_support:
                formatted_parts.extend([
                    template.system_prefix,
                    content,
                    template.system_suffix
                ])
            elif role == "user":
                formatted_parts.extend([
                    template.user_prefix,
                    content,
                    template.user_suffix
                ])
            elif role == "assistant":
                formatted_parts.extend([
                    template.assistant_prefix,
                    content,
                    template.assistant_suffix
                ])
        
        # Add generation prompt for new assistant response
        if add_generation_prompt and template.generation_prompt:
            formatted_parts.append(template.generation_prompt)
        
        return "".join(formatted_parts)
    
    def inject_system_prompt_for_non_supporting_models(self, 
                                                     messages: List[Dict[str, str]], 
                                                     template: ChatTemplate) -> List[Dict[str, str]]:
        """Inject system prompt into user message for models that don't support system roles"""
        
        if template.system_support:
            return messages  # No need to inject
        
        # Find system message
        system_message = None
        other_messages = []
        
        for msg in messages:
            if msg.get("role") == "system":
                system_message = msg
            else:
                other_messages.append(msg)
        
        if not system_message:
            return messages  # No system message to inject
        
        # Inject system content into first user message
        if other_messages and other_messages[0].get("role") == "user":
            injected_content = f"{system_message['content']}\n\n{other_messages[0]['content']}"
            other_messages[0] = {
                "role": "user",
                "content": injected_content
            }
        
        return other_messages
    
    def get_supported_models(self) -> Dict[TemplateType, List[str]]:
        """Get list of supported model patterns by template type"""
        reverse_mapping = {}
        
        for model_pattern, template_type in self.model_mappings.items():
            if template_type not in reverse_mapping:
                reverse_mapping[template_type] = []
            reverse_mapping[template_type].append(model_pattern)
        
        return reverse_mapping


# Convenience functions for easy integration
def get_model_template(model_name: str = None, tokenizer = None) -> ChatTemplate:
    """Get chat template for a model"""
    manager = ModelTemplateManager()
    return manager.get_template(model_name, tokenizer)


def format_conversation_for_model(messages: List[Dict[str, str]], 
                                model_name: str = None,
                                tokenizer = None,
                                add_generation_prompt: bool = True) -> str:
    """Format conversation with appropriate template for the model"""
    manager = ModelTemplateManager()
    template = manager.get_template(model_name, tokenizer)
    
    # Handle system prompt injection for non-supporting models
    processed_messages = manager.inject_system_prompt_for_non_supporting_models(messages, template)
    
    return manager.format_conversation(processed_messages, template, add_generation_prompt)


if __name__ == "__main__":
    # Test the template manager
    manager = ModelTemplateManager()
    
    test_messages = [
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "Hello!"},
        {"role": "assistant", "content": "Hi there! How can I help you?"},
        {"role": "user", "content": "Tell me about Python."}
    ]
    
    print("Testing different chat templates:\n")
    
    for template_type in TemplateType:
        template = manager.templates[template_type]
        formatted = manager.format_conversation(test_messages, template)
        print(f"=== {template.name} ({template_type.value}) ===")
        print(formatted)
        print()