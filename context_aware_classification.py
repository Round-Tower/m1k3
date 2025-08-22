#!/usr/bin/env python3
"""
Context-Aware Classification Engine
Integrates intent classification with adaptive model configuration and WebSocket context data
"""

import asyncio
import json
import logging
from typing import Dict, List, Any, Optional, Union
from dataclasses import dataclass, asdict
from datetime import datetime
import threading

# Import our systems
from intent_classification_system import (
    IntentClassificationEngine, 
    UserIntent, 
    ResponseStrategy, 
    IntentClassification,
    basic_context_enricher
)

@dataclass 
class AdaptiveGenerationConfig:
    """Enhanced generation configuration with intent awareness"""
    # Core generation parameters
    max_new_tokens: int
    do_sample: bool
    temperature: Optional[float] = None
    top_p: Optional[float] = None
    top_k: Optional[int] = None
    repetition_penalty: float = 1.0
    no_repeat_ngram_size: int = 0
    num_beams: int = 1
    
    # Advanced parameters
    enable_thinking_mode: bool = False
    show_reasoning: bool = False
    quality_threshold: float = 0.7
    allow_long_responses: bool = True
    
    # Metadata for transparency
    intent: str = "unknown"
    confidence: float = 0.5
    response_strategy: str = "balanced"
    context_factors: Dict[str, Any] = None
    reasoning: str = "Default configuration"
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary format expected by AI inference"""
        result = asdict(self)
        
        # Create metadata section
        result['_metadata'] = {
            'intent': self.intent,
            'confidence': self.confidence,
            'response_strategy': self.response_strategy,
            'context_factors': self.context_factors or {},
            'reasoning': self.reasoning,
            'generation_timestamp': datetime.now().isoformat()
        }
        
        # Remove metadata fields from main dict
        for field in ['intent', 'confidence', 'response_strategy', 'context_factors', 'reasoning']:
            if field in result:
                del result[field]
        
        return result

class ContextAwareClassificationEngine:
    """Main engine that combines intent classification with adaptive configuration"""
    
    def __init__(self):
        self.intent_engine = IntentClassificationEngine()
        self.intent_engine.add_context_enricher(basic_context_enricher)
        
        # Strategy to generation parameter mappings
        self.strategy_configs = self._initialize_strategy_configs()
        
        # WebSocket context integration
        self.websocket_context = {}
        self.context_lock = threading.Lock()
        
        # Logging
        self.logger = logging.getLogger(__name__)
        
    def _initialize_strategy_configs(self) -> Dict[ResponseStrategy, Dict[str, Any]]:
        """Initialize generation parameters for each response strategy"""
        return {
            ResponseStrategy.DETERMINISTIC: {
                'do_sample': False,
                'temperature': None,
                'top_p': None,
                'top_k': None,
                'repetition_penalty': 1.1,
                'no_repeat_ngram_size': 2,
                'num_beams': 1,
                'max_new_tokens': 150,
                'enable_thinking_mode': False,
                'show_reasoning': False,
                'quality_threshold': 0.8,
                'allow_long_responses': False
            },
            
            ResponseStrategy.BALANCED: {
                'do_sample': True,
                'temperature': 0.7,
                'top_p': 0.9,
                'top_k': 40,
                'repetition_penalty': 1.05,
                'no_repeat_ngram_size': 2,
                'num_beams': 1,
                'max_new_tokens': 300,
                'enable_thinking_mode': True,
                'show_reasoning': False,
                'quality_threshold': 0.7,
                'allow_long_responses': True
            },
            
            ResponseStrategy.CREATIVE: {
                'do_sample': True,
                'temperature': 0.9,
                'top_p': 0.95,
                'top_k': 50,
                'repetition_penalty': 1.02,
                'no_repeat_ngram_size': 3,
                'num_beams': 1,
                'max_new_tokens': 500,
                'enable_thinking_mode': True,
                'show_reasoning': False,
                'quality_threshold': 0.6,
                'allow_long_responses': True
            },
            
            ResponseStrategy.CONVERSATIONAL: {
                'do_sample': True,
                'temperature': 0.8,
                'top_p': 0.9,
                'top_k': 35,
                'repetition_penalty': 1.03,
                'no_repeat_ngram_size': 2,
                'num_beams': 1,
                'max_new_tokens': 250,
                'enable_thinking_mode': False,
                'show_reasoning': False,
                'quality_threshold': 0.7,
                'allow_long_responses': True
            },
            
            ResponseStrategy.STRUCTURED: {
                'do_sample': True,
                'temperature': 0.6,
                'top_p': 0.85,
                'top_k': 30,
                'repetition_penalty': 1.1,
                'no_repeat_ngram_size': 2,
                'num_beams': 2,
                'max_new_tokens': 400,
                'enable_thinking_mode': True,
                'show_reasoning': True,
                'quality_threshold': 0.8,
                'allow_long_responses': True
            },
            
            ResponseStrategy.BRIEF: {
                'do_sample': False,
                'temperature': None,
                'top_p': None,
                'top_k': None,
                'repetition_penalty': 1.2,
                'no_repeat_ngram_size': 2,
                'num_beams': 1,
                'max_new_tokens': 100,
                'enable_thinking_mode': False,
                'show_reasoning': False,
                'quality_threshold': 0.8,
                'allow_long_responses': False
            },
            
            ResponseStrategy.DETAILED: {
                'do_sample': True,
                'temperature': 0.7,
                'top_p': 0.9,
                'top_k': 40,
                'repetition_penalty': 1.05,
                'no_repeat_ngram_size': 2,
                'num_beams': 1,
                'max_new_tokens': 600,
                'enable_thinking_mode': True,
                'show_reasoning': True,
                'quality_threshold': 0.75,
                'allow_long_responses': True
            }
        }
    
    def update_websocket_context(self, context_data: Dict[str, Any]) -> None:
        """Update context from WebSocket data"""
        with self.context_lock:
            self.websocket_context.update(context_data)
            self.logger.debug(f"Updated WebSocket context: {context_data}")
    
    def get_websocket_enricher(self):
        """Create context enricher that uses WebSocket data"""
        def websocket_enricher(query: str, existing_context: Dict[str, Any]) -> Dict[str, Any]:
            with self.context_lock:
                enriched_context = {}
                
                # Add recent WebSocket context data
                if self.websocket_context:
                    enriched_context['websocket_data'] = self.websocket_context.copy()
                    
                    # Extract specific context factors
                    if 'user_mood' in self.websocket_context:
                        enriched_context['user_mood'] = self.websocket_context['user_mood']
                        
                    if 'task_urgency' in self.websocket_context:
                        if self.websocket_context['task_urgency'] == 'high':
                            enriched_context['urgent'] = True
                            
                    if 'conversation_topic' in self.websocket_context:
                        topic = self.websocket_context['conversation_topic']
                        if 'technical' in topic or 'debug' in topic:
                            enriched_context['technical_context'] = True
                        elif 'creative' in topic or 'story' in topic:
                            enriched_context['creative_context'] = True
                            
                    if 'typing_speed' in self.websocket_context:
                        speed = self.websocket_context['typing_speed']
                        if isinstance(speed, (int, float)) and speed > 60:
                            enriched_context['fast_typing'] = True
                        
                return enriched_context
                
        return websocket_enricher
    
    def get_optimal_config(self, query: str, model_name: str = "unknown", context: List[Dict] = None) -> Dict[str, Any]:
        """Get optimal generation configuration based on intent and context"""
        
        # Prepare context for intent classification
        classification_context = {}
        if context:
            # Extract context information from conversation history
            recent_user_messages = [msg for msg in context[-3:] if msg.get('role') == 'user']
            if recent_user_messages:
                classification_context['conversation_history'] = recent_user_messages
                
        # Add WebSocket enricher to intent engine if not already added
        websocket_enricher = self.get_websocket_enricher()
        if websocket_enricher not in self.intent_engine.context_enrichers:
            self.intent_engine.add_context_enricher(websocket_enricher)
        
        # Classify intent
        intent_classification = self.intent_engine.classify_intent(query, classification_context)
        
        # Get base configuration for the response strategy
        base_config = self.strategy_configs[intent_classification.response_strategy].copy()
        
        # Context-aware adjustments
        adjustments = self._apply_context_adjustments(intent_classification, classification_context, model_name)
        base_config.update(adjustments)
        
        # Create enhanced configuration object
        config = AdaptiveGenerationConfig(
            intent=intent_classification.intent.value,
            confidence=intent_classification.confidence,
            response_strategy=intent_classification.response_strategy.value,
            context_factors=intent_classification.context_factors,
            reasoning=f"Intent: {intent_classification.intent.value} ({intent_classification.confidence:.3f}) -> Strategy: {intent_classification.response_strategy.value}",
            **base_config
        )
        
        self.logger.info(f"🎯 Intent: {intent_classification.intent.value} ({intent_classification.confidence:.3f}) -> {intent_classification.response_strategy.value}")
        
        return config.to_dict()
    
    def _apply_context_adjustments(self, classification: IntentClassification, context: Dict[str, Any], model_name: str) -> Dict[str, Any]:
        """Apply context-specific adjustments to generation parameters"""
        adjustments = {}
        
        # Model-specific adjustments
        if 'qwen' in model_name.lower():
            # Qwen models work better with slightly lower temperature
            if 'temperature' in adjustments:
                adjustments['temperature'] = max(0.1, adjustments.get('temperature', 0.7) - 0.1)
        elif 'tinyllama' in model_name.lower():
            # TinyLlama needs more conservative parameters
            adjustments['max_new_tokens'] = min(adjustments.get('max_new_tokens', 300), 200)
            if 'temperature' in adjustments:
                adjustments['temperature'] = min(adjustments.get('temperature', 0.7), 0.8)
        
        # Context-based adjustments
        context_factors = classification.context_factors
        
        if context_factors.get('urgent', False):
            # Urgent requests: shorter, more direct
            adjustments['max_new_tokens'] = min(adjustments.get('max_new_tokens', 300), 150)
            adjustments['enable_thinking_mode'] = False
            adjustments['show_reasoning'] = False
            
        if context_factors.get('learning_mode', False):
            # Learning requests: more detailed and structured
            adjustments['max_new_tokens'] = max(adjustments.get('max_new_tokens', 300), 400)
            adjustments['enable_thinking_mode'] = True
            adjustments['show_reasoning'] = True
            
        if context_factors.get('casual_mode', False):
            # Casual conversation: more natural and flowing
            if adjustments.get('do_sample', True):
                adjustments['temperature'] = min(adjustments.get('temperature', 0.7) + 0.1, 0.9)
            adjustments['enable_thinking_mode'] = False
            
        if context_factors.get('technical_context', False):
            # Technical queries: more structured and precise
            adjustments['repetition_penalty'] = max(adjustments.get('repetition_penalty', 1.05), 1.1)
            adjustments['enable_thinking_mode'] = True
            
        # WebSocket context adjustments
        websocket_data = context.get('websocket_data', {})
        
        if websocket_data.get('user_mood') == 'focused':
            # User is focused: provide comprehensive responses
            adjustments['max_new_tokens'] = max(adjustments.get('max_new_tokens', 300), 350)
            adjustments['quality_threshold'] = max(adjustments.get('quality_threshold', 0.7), 0.8)
            
        if websocket_data.get('fast_typing', False):
            # User typing fast: they want quick responses
            adjustments['max_new_tokens'] = min(adjustments.get('max_new_tokens', 300), 200)
            
        return adjustments
    
    def get_classification_explanation(self, query: str) -> Dict[str, Any]:
        """Get detailed explanation of classification decision"""
        
        classification = self.intent_engine.classify_intent(query)
        
        return {
            'query': query,
            'classification': classification.to_dict(),
            'strategy_config': self.strategy_configs[classification.response_strategy],
            'confidence_level': self.intent_engine.get_confidence_level(classification.confidence).value,
            'timestamp': datetime.now().isoformat()
        }

# Testing functionality
def test_context_aware_classification():
    """Test the context-aware classification engine"""
    print("🧠 Testing Context-Aware Classification Engine")
    print("=" * 60)
    
    engine = ContextAwareClassificationEngine()
    
    # Test queries
    test_queries = [
        ("What is 25 * 17?", "Math calculation test"),
        ("How do I fix a segmentation fault in C?", "Technical debugging test"),  
        ("Write a creative story about space exploration", "Creative writing test"),
        ("Hello! How's your day going?", "Casual conversation test"),
        ("Explain quantum computing in simple terms", "Educational explanation test"),
    ]
    
    for query, description in test_queries:
        print(f"\n📝 Test: {description}")
        print(f"Query: \"{query}\"")
        
        # Get optimal configuration
        config = engine.get_optimal_config(query, "qwen/qwen3-0.6b", [])
        
        metadata = config.get('_metadata', {})
        
        print(f"🎯 Intent: {metadata.get('intent', 'unknown')}")
        print(f"📊 Confidence: {metadata.get('confidence', 0):.3f}")
        print(f"🎭 Strategy: {metadata.get('response_strategy', 'unknown')}")
        print(f"🔧 Generation params:")
        for key, value in config.items():
            if key != '_metadata' and value is not None:
                print(f"   {key}: {value}")
        print(f"💭 Reasoning: {metadata.get('reasoning', 'No reasoning provided')}")
    
    # Test with WebSocket context
    print(f"\n" + "="*60)
    print("🌐 Testing WebSocket Context Integration")
    
    # Simulate WebSocket context data
    engine.update_websocket_context({
        'user_mood': 'focused',
        'task_urgency': 'high', 
        'conversation_topic': 'technical_debugging',
        'typing_speed': 75
    })
    
    test_query = "Help me optimize this algorithm"
    config = engine.get_optimal_config(test_query, "qwen/qwen3-0.6b", [])
    metadata = config.get('_metadata', {})
    
    print(f"📝 Query: \"{test_query}\"")
    print(f"🎯 Intent: {metadata.get('intent', 'unknown')}")
    print(f"📊 Confidence: {metadata.get('confidence', 0):.3f}")
    print(f"🎭 Strategy: {metadata.get('response_strategy', 'unknown')}")
    print(f"🌐 WebSocket factors: {metadata.get('context_factors', {}).get('websocket_data', {})}")
    print(f"💭 Reasoning: {metadata.get('reasoning', 'No reasoning provided')}")
    
if __name__ == "__main__":
    test_context_aware_classification()