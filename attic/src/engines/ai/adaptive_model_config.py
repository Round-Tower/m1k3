#!/usr/bin/env python3
"""
Adaptive Model Configuration for M1K3
Intelligent parameter optimization based on task type, model capabilities, and confidence levels
"""

import re
import math
from typing import Dict, List, Optional, Tuple, Union
from dataclasses import dataclass
from enum import Enum

class TaskType(Enum):
    """Classification of user queries for appropriate parameter selection"""
    FACTUAL = "factual"           # Simple facts, definitions, basic information
    MATHEMATICAL = "mathematical"  # Math problems, calculations, quantitative analysis
    LOGICAL = "logical"           # Reasoning, logic puzzles, step-by-step analysis
    CREATIVE = "creative"         # Writing, brainstorming, artistic tasks
    CONVERSATIONAL = "conversational"  # Chat, greetings, casual interaction
    CODING = "coding"            # Programming, technical explanations
    ANALYTICAL = "analytical"    # Complex analysis, comparisons, evaluations
    INSTRUCTIONAL = "instructional"  # How-to, explanations, tutorials

class ConfidenceLevel(Enum):
    """Confidence levels for parameter scaling"""
    LOW = "low"           # Conservative parameters, heavy filtering
    MEDIUM = "medium"     # Balanced parameters, standard filtering
    HIGH = "high"         # Optimal parameters, minimal filtering
    EXPERT = "expert"     # Maximum parameters, quality-focused filtering

class ModelCapability(Enum):
    """Model capability categories"""
    REASONING = "reasoning"       # Strong logical reasoning (Qwen3, Phi-3)
    CONVERSATIONAL = "conversational"  # Natural conversation (DialoGPT)
    INSTRUCTION = "instruction"   # Following instructions (Gemma)
    GENERAL = "general"          # General purpose (TinyLlama, others)
    MATHEMATICAL = "mathematical" # Math and calculations (Phi-3)

@dataclass
class AdaptiveParameters:
    """Adaptive generation parameters for a specific task and confidence level"""
    max_new_tokens: int
    temperature: Optional[float]
    top_p: Optional[float]
    top_k: Optional[int]
    do_sample: bool
    repetition_penalty: float
    no_repeat_ngram_size: int
    num_beams: int
    enable_thinking_mode: bool
    show_reasoning: bool
    quality_threshold: float  # Minimum quality score required
    allow_long_responses: bool

@dataclass
class ModelProfile:
    """Profile of a model's capabilities and optimal parameters"""
    name: str
    capabilities: List[ModelCapability]
    optimal_tasks: List[TaskType]
    base_quality_score: float
    max_reliable_tokens: int
    supports_reasoning: bool
    supports_thinking_mode: bool
    reasoning_quality: float

class TaskClassifier:
    """Intelligent classification of user queries"""
    
    def __init__(self):
        self.patterns = {
            TaskType.MATHEMATICAL: [
                r'\d+\s*[+\-*/÷×]\s*\d+',  # Basic arithmetic
                r'calculate|compute|solve|equation|formula',
                r'what is \d+.*[\+\-\*/].*\d+',
                r'\b(sum|product|quotient|difference)\b',
                r'percentage|percent|%',
                r'\b(square|cube|root|power)\b',
                r'statistics|average|mean|median',
                r'probability|odds|chance',
                r'\barea.*circle.*radius\b',  # Geometry problems
                r'\bcircle.*radius\s*\d+\b',  # Circle with radius
                r'\b\d+\s*\*\s*\d+\b'  # Multiplication expressions
            ],
            TaskType.LOGICAL: [
                r'\b(if.*then|therefore|because|since|given that)\b',
                r'\b(analyze|reason|logic|deduce|infer)\b',
                r'\b(compare|contrast|evaluate|assess)\b',
                r'\b(step by step|systematic|methodical)\b',
                r'what can we conclude|what does this mean',
                r'explain why|explain how|what causes',
                r'logical fallacy|argument|premise',
                r'\ball\s+birds.*fly\b',  # Logic puzzle patterns
                r'\bpenguins.*birds\b',  # Common logic example
                r'\bif\s+all.*and.*then\b'  # Conditional logic
            ],
            TaskType.CREATIVE: [
                r'\b(write|create|compose|generate)\b.*\b(story|poem|song|script)\b',
                r'\b(creative|artistic|imaginative|original)\b',
                r'\b(brainstorm|ideas|suggestions|alternatives)\b',
                r'\b(design|invent|innovate)\b',
                r'write a.*story|create a.*character',
                r'come up with|think of.*ideas'
            ],
            TaskType.CODING: [
                r'\b(code|program|script|function|algorithm)\b',
                r'\b(python|javascript|java|c\+\+|html|css)\b',
                r'write.*code|programming|software',
                r'\b(debug|fix.*bug|error)\b',
                r'function that|method to|class for',
                r'regular expression|regex',
                r'\bwrite.*python.*function\b',  # Python function request
                r'\bfind.*maximum.*element.*list\b',  # Algorithm request
                r'\bfunction.*to.*sort\b'  # Sorting request
            ],
            TaskType.CONVERSATIONAL: [
                r'\b(hello|hi|hey|good morning|good afternoon)\b',
                r'\b(how are you|what\'s up|how\'s it going)\b',
                r'\b(thanks|thank you|please|sorry)\b',
                r'\b(chat|talk|conversation)\b',
                r'^(yes|no|ok|okay|sure)$'
            ],
            TaskType.FACTUAL: [
                r'\b(what is|who is|when is|where is|why is)\b',
                r'\b(define|definition|meaning of)\b',
                r'\b(capital of|population of|located in)\b',
                r'tell me about|information about',
                r'fact|facts about'
            ],
            TaskType.INSTRUCTIONAL: [
                r'\b(how to|how do|how can)\b',
                r'\b(explain|show me|teach me|guide)\b',
                r'\b(steps|instructions|tutorial|walkthrough)\b',
                r'can you help.*with|help me.*to'
            ]
        }
    
    def classify_query(self, query: str, context: List[str] = None) -> Tuple[TaskType, float]:
        """
        Classify a query and return task type with confidence score
        
        Returns:
            Tuple of (TaskType, confidence_score)
        """
        query_lower = query.lower().strip()
        scores = {}
        
        # High-priority math detection - catch common patterns early
        high_priority_math_patterns = [
            r'what\s+is\s+\d+\s*[+\-*/÷×]\s*\d+',  # "what is 2 + 2"
            r'calculate\s+\d+\s*[+\-*/÷×]\s*\d+',   # "calculate 5 * 6"
            r'^\d+\s*[+\-*/÷×]\s*\d+\s*[=?]?$',     # "15 * 23" or "15 * 23 ="
        ]
        
        for pattern in high_priority_math_patterns:
            if re.search(pattern, query_lower):
                return TaskType.MATHEMATICAL, 0.95  # High confidence math detection
        
        # Score each task type based on pattern matches
        for task_type, patterns in self.patterns.items():
            score = 0
            match_weight = 1.0
            
            for pattern in patterns:
                matches = len(re.findall(pattern, query_lower, re.IGNORECASE))
                score += matches * match_weight
            
            # Weight scores by task type importance for better classification
            if task_type == TaskType.MATHEMATICAL:
                score *= 1.5  # Boost math classification
            elif task_type == TaskType.CODING:
                score *= 1.3  # Boost coding classification
                
            # Normalize score by pattern count (but less aggressively)
            if patterns:
                scores[task_type] = score / max(len(patterns) * 0.7, 1.0)
        
        # Apply contextual adjustments
        if context:
            scores = self._adjust_for_context(scores, context)
        
        # Find highest scoring task type
        if not scores or max(scores.values()) == 0:
            # Default classification based on query length and structure
            if len(query.split()) <= 3 and any(word in query_lower for word in ['hello', 'hi', 'thanks', 'yes', 'no']):
                return TaskType.CONVERSATIONAL, 0.8
            elif '?' in query and len(query.split()) <= 10:
                return TaskType.FACTUAL, 0.6
            else:
                return TaskType.ANALYTICAL, 0.4
        
        best_task = max(scores.items(), key=lambda x: x[1])
        confidence = min(best_task[1], 1.0)
        
        return best_task[0], confidence
    
    def _adjust_for_context(self, scores: Dict[TaskType, float], context: List[str]) -> Dict[TaskType, float]:
        """Adjust scores based on conversation context"""
        if not context:
            return scores
        
        # Look for patterns in recent context
        recent_context = " ".join(context[-3:]).lower()
        
        # If recent context was mathematical, boost math score
        if any(word in recent_context for word in ['calculate', 'number', 'math', 'equation']):
            scores[TaskType.MATHEMATICAL] = scores.get(TaskType.MATHEMATICAL, 0) + 0.2
        
        # If recent context was conversational, boost conversational score
        if any(word in recent_context for word in ['hello', 'chat', 'talk', 'conversation']):
            scores[TaskType.CONVERSATIONAL] = scores.get(TaskType.CONVERSATIONAL, 0) + 0.2
        
        return scores

class AdaptiveModelConfig:
    """
    Adaptive model configuration system that optimizes parameters based on
    task type, model capabilities, and confidence levels
    """
    
    def __init__(self):
        self.classifier = TaskClassifier()
        self.model_profiles = self._create_model_profiles()
        self.parameter_profiles = self._create_parameter_profiles()
        
        # Quality and trust metrics
        self.quality_history = {}
        self.trust_scores = {}
        
    def _create_model_profiles(self) -> Dict[str, ModelProfile]:
        """Create detailed profiles for each model"""
        return {
            'qwen/qwen3-0.6b': ModelProfile(
                name='Qwen3-0.6B',
                capabilities=[ModelCapability.REASONING, ModelCapability.MATHEMATICAL],
                optimal_tasks=[TaskType.LOGICAL, TaskType.MATHEMATICAL, TaskType.ANALYTICAL, TaskType.FACTUAL, TaskType.INSTRUCTIONAL],
                base_quality_score=0.75,
                max_reliable_tokens=150,  # Increased from 30
                supports_reasoning=True,
                supports_thinking_mode=True,
                reasoning_quality=0.8
            ),
            'microsoft/phi-3-mini-4k-instruct': ModelProfile(
                name='Phi-3-Mini',
                capabilities=[ModelCapability.REASONING, ModelCapability.MATHEMATICAL, ModelCapability.INSTRUCTION],
                optimal_tasks=[TaskType.MATHEMATICAL, TaskType.LOGICAL, TaskType.INSTRUCTIONAL, TaskType.CODING],
                base_quality_score=0.85,
                max_reliable_tokens=200,
                supports_reasoning=True,
                supports_thinking_mode=True,
                reasoning_quality=0.9
            ),
            'google/gemma-2-2b-it': ModelProfile(
                name='Gemma-2-2B',
                capabilities=[ModelCapability.INSTRUCTION, ModelCapability.CONVERSATIONAL],
                optimal_tasks=[TaskType.INSTRUCTIONAL, TaskType.CONVERSATIONAL, TaskType.CREATIVE],
                base_quality_score=0.8,
                max_reliable_tokens=120,
                supports_reasoning=True,
                supports_thinking_mode=False,
                reasoning_quality=0.7
            ),
            'tinyllama/tinyllama-1.1b-chat-v1.0': ModelProfile(
                name='TinyLlama-1.1B',
                capabilities=[ModelCapability.CONVERSATIONAL, ModelCapability.GENERAL],
                optimal_tasks=[TaskType.CONVERSATIONAL, TaskType.FACTUAL],
                base_quality_score=0.65,
                max_reliable_tokens=100,
                supports_reasoning=False,
                supports_thinking_mode=False,
                reasoning_quality=0.5
            ),
            'microsoft/dialogpt-small': ModelProfile(
                name='DialoGPT-Small',
                capabilities=[ModelCapability.CONVERSATIONAL],
                optimal_tasks=[TaskType.CONVERSATIONAL, TaskType.CREATIVE],
                base_quality_score=0.7,
                max_reliable_tokens=80,  # Increased from 60
                supports_reasoning=False,
                supports_thinking_mode=False,
                reasoning_quality=0.4
            )
        }
    
    def _create_parameter_profiles(self) -> Dict[Tuple[TaskType, ConfidenceLevel], AdaptiveParameters]:
        """Create parameter profiles for different task types and confidence levels"""
        profiles = {}
        
        # Mathematical tasks - need deterministic, precise calculation
        profiles[(TaskType.MATHEMATICAL, ConfidenceLevel.HIGH)] = AdaptiveParameters(
            max_new_tokens=80, temperature=None, top_p=None, top_k=None,
            do_sample=False, repetition_penalty=1.2, no_repeat_ngram_size=2, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.8,
            allow_long_responses=False
        )
        
        profiles[(TaskType.MATHEMATICAL, ConfidenceLevel.MEDIUM)] = AdaptiveParameters(
            max_new_tokens=100, temperature=None, top_p=None, top_k=None,
            do_sample=False, repetition_penalty=1.25, no_repeat_ngram_size=2, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.7,
            allow_long_responses=False
        )
        
        profiles[(TaskType.MATHEMATICAL, ConfidenceLevel.LOW)] = AdaptiveParameters(
            max_new_tokens=50, temperature=None, top_p=None, top_k=None,
            do_sample=False, repetition_penalty=1.3, no_repeat_ngram_size=2, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.6,
            allow_long_responses=False
        )
        
        # Logical reasoning tasks
        profiles[(TaskType.LOGICAL, ConfidenceLevel.HIGH)] = AdaptiveParameters(
            max_new_tokens=250, temperature=0.4, top_p=0.85, top_k=30,
            do_sample=True, repetition_penalty=1.1, no_repeat_ngram_size=4, num_beams=1,
            enable_thinking_mode=True, show_reasoning=True, quality_threshold=0.8,
            allow_long_responses=True
        )
        
        profiles[(TaskType.LOGICAL, ConfidenceLevel.MEDIUM)] = AdaptiveParameters(
            max_new_tokens=180, temperature=0.3, top_p=0.8, top_k=25,
            do_sample=True, repetition_penalty=1.15, no_repeat_ngram_size=3, num_beams=1,
            enable_thinking_mode=True, show_reasoning=False, quality_threshold=0.7,
            allow_long_responses=False
        )
        
        # Creative tasks - need more freedom
        profiles[(TaskType.CREATIVE, ConfidenceLevel.HIGH)] = AdaptiveParameters(
            max_new_tokens=300, temperature=0.8, top_p=0.95, top_k=50,
            do_sample=True, repetition_penalty=1.05, no_repeat_ngram_size=2, num_beams=1,
            enable_thinking_mode=True, show_reasoning=False, quality_threshold=0.6,
            allow_long_responses=True
        )
        
        profiles[(TaskType.CREATIVE, ConfidenceLevel.MEDIUM)] = AdaptiveParameters(
            max_new_tokens=200, temperature=0.7, top_p=0.9, top_k=40,
            do_sample=True, repetition_penalty=1.1, no_repeat_ngram_size=3, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.5,
            allow_long_responses=True
        )
        
        # Conversational tasks - natural and flowing
        profiles[(TaskType.CONVERSATIONAL, ConfidenceLevel.HIGH)] = AdaptiveParameters(
            max_new_tokens=120, temperature=0.6, top_p=0.9, top_k=35,
            do_sample=True, repetition_penalty=1.2, no_repeat_ngram_size=4, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.5,
            allow_long_responses=False
        )
        
        # Factual tasks - conservative but informative
        profiles[(TaskType.FACTUAL, ConfidenceLevel.HIGH)] = AdaptiveParameters(
            max_new_tokens=100, temperature=0.3, top_p=0.8, top_k=25,
            do_sample=True, repetition_penalty=1.2, no_repeat_ngram_size=3, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.8,
            allow_long_responses=False
        )
        
        profiles[(TaskType.FACTUAL, ConfidenceLevel.MEDIUM)] = AdaptiveParameters(
            max_new_tokens=120, temperature=0.2, top_p=0.7, top_k=20,
            do_sample=True, repetition_penalty=1.25, no_repeat_ngram_size=3, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.7,
            allow_long_responses=True
        )
        
        profiles[(TaskType.FACTUAL, ConfidenceLevel.LOW)] = AdaptiveParameters(
            max_new_tokens=120, temperature=0.1, top_p=0.6, top_k=15,
            do_sample=True, repetition_penalty=1.3, no_repeat_ngram_size=2, num_beams=1,
            enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.6,
            allow_long_responses=True
        )
        
        # Coding tasks - need detailed explanations
        profiles[(TaskType.CODING, ConfidenceLevel.HIGH)] = AdaptiveParameters(
            max_new_tokens=250, temperature=0.4, top_p=0.85, top_k=30,
            do_sample=True, repetition_penalty=1.1, no_repeat_ngram_size=3, num_beams=1,
            enable_thinking_mode=True, show_reasoning=True, quality_threshold=0.8,
            allow_long_responses=True
        )
        
        # Fill in missing combinations with sensible defaults
        for task_type in TaskType:
            for conf_level in ConfidenceLevel:
                if (task_type, conf_level) not in profiles:
                    # Generate reasonable defaults based on existing patterns
                    profiles[(task_type, conf_level)] = self._generate_default_params(task_type, conf_level)
        
        return profiles
    
    def _generate_default_params(self, task_type: TaskType, conf_level: ConfidenceLevel) -> AdaptiveParameters:
        """Generate sensible default parameters for missing combinations"""
        
        # Task-specific token adjustments for LOW confidence
        if conf_level == ConfidenceLevel.LOW:
            # Adjust tokens based on task complexity needs
            if task_type in [TaskType.CODING, TaskType.INSTRUCTIONAL]:
                max_tokens = 120  # Code and explanations need more space
            elif task_type in [TaskType.ANALYTICAL, TaskType.LOGICAL]:
                max_tokens = 100  # Reasoning needs moderate space
            else:
                max_tokens = 60   # Simple tasks can be shorter
                
            return AdaptiveParameters(
                max_new_tokens=max_tokens, temperature=None, top_p=None, top_k=None,
                do_sample=False, repetition_penalty=1.25, no_repeat_ngram_size=2, num_beams=1,
                enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.8,
                allow_long_responses=(task_type in [TaskType.CODING, TaskType.INSTRUCTIONAL, TaskType.ANALYTICAL])
            )
        elif conf_level == ConfidenceLevel.MEDIUM:
            return AdaptiveParameters(
                max_new_tokens=120, temperature=0.4, top_p=0.8, top_k=30,
                do_sample=True, repetition_penalty=1.15, no_repeat_ngram_size=3, num_beams=1,
                enable_thinking_mode=False, show_reasoning=False, quality_threshold=0.7,
                allow_long_responses=False
            )
        else:  # HIGH or EXPERT
            return AdaptiveParameters(
                max_new_tokens=180, temperature=0.5, top_p=0.85, top_k=35,
                do_sample=True, repetition_penalty=1.1, no_repeat_ngram_size=3, num_beams=1,
                enable_thinking_mode=True, show_reasoning=False, quality_threshold=0.6,
                allow_long_responses=True
            )
    
    def get_optimal_config(self, query: str, model_name: str, 
                          context: List[str] = None, 
                          user_confidence: Optional[float] = None) -> Dict:
        """
        Get optimal configuration for a specific query and model
        
        Args:
            query: User's query
            model_name: Model identifier
            context: Previous conversation context
            user_confidence: Optional user-specified confidence override
            
        Returns:
            Dictionary with optimized parameters
        """
        
        # Classify the query
        task_type, task_confidence = self.classifier.classify_query(query, context)
        
        # Get model profile
        model_key = model_name.lower()
        model_profile = self.model_profiles.get(model_key)
        
        if not model_profile:
            # Unknown model - use conservative defaults
            model_profile = ModelProfile(
                name=model_name, capabilities=[ModelCapability.GENERAL],
                optimal_tasks=[TaskType.FACTUAL], base_quality_score=0.5,
                max_reliable_tokens=80, supports_reasoning=False,
                supports_thinking_mode=False, reasoning_quality=0.5
            )
        
        # Determine confidence level
        confidence_level = self._determine_confidence_level(
            task_type, task_confidence, model_profile, user_confidence
        )
        
        # Get base parameters for task and confidence level
        param_key = (task_type, confidence_level)
        if param_key in self.parameter_profiles:
            params = self.parameter_profiles[param_key]
        else:
            params = self._generate_default_params(task_type, confidence_level)
        
        # Adjust parameters based on model capabilities
        adjusted_params = self._adjust_for_model_capabilities(params, model_profile, task_type)
        
        # Convert to generation parameters dictionary
        config = self._to_generation_config(adjusted_params, model_profile)
        
        # Add metadata for monitoring and debugging
        config['_metadata'] = {
            'task_type': task_type.value,
            'confidence_level': confidence_level.value,
            'task_confidence': task_confidence,
            'model_profile': model_profile.name,
            'thinking_mode_enabled': adjusted_params.enable_thinking_mode,
            'show_reasoning': adjusted_params.show_reasoning
        }
        
        return config
    
    def _determine_confidence_level(self, task_type: TaskType, task_confidence: float,
                                  model_profile: ModelProfile, 
                                  user_confidence: Optional[float] = None) -> ConfidenceLevel:
        """Determine appropriate confidence level"""
        
        if user_confidence is not None:
            # User override
            if user_confidence >= 0.9:
                return ConfidenceLevel.EXPERT
            elif user_confidence >= 0.7:
                return ConfidenceLevel.HIGH
            elif user_confidence >= 0.5:
                return ConfidenceLevel.MEDIUM
            else:
                return ConfidenceLevel.LOW
        
        # Calculate combined confidence score
        combined_confidence = (
            task_confidence * 0.4 +  # How sure we are about task type
            model_profile.base_quality_score * 0.3 +  # Model reliability
            (1.0 if task_type in model_profile.optimal_tasks else 0.5) * 0.3  # Task-model fit
        )
        
        # Map to confidence levels
        if combined_confidence >= 0.85:
            return ConfidenceLevel.HIGH
        elif combined_confidence >= 0.65:
            return ConfidenceLevel.MEDIUM
        else:
            return ConfidenceLevel.LOW
    
    def _adjust_for_model_capabilities(self, params: AdaptiveParameters, 
                                     model_profile: ModelProfile, 
                                     task_type: TaskType) -> AdaptiveParameters:
        """Adjust parameters based on specific model capabilities"""
        
        adjusted = AdaptiveParameters(
            max_new_tokens=min(params.max_new_tokens, model_profile.max_reliable_tokens),
            temperature=params.temperature,
            top_p=params.top_p,
            top_k=params.top_k,
            do_sample=params.do_sample,
            repetition_penalty=params.repetition_penalty,
            no_repeat_ngram_size=params.no_repeat_ngram_size,
            num_beams=params.num_beams,
            enable_thinking_mode=params.enable_thinking_mode and model_profile.supports_thinking_mode,
            show_reasoning=params.show_reasoning and model_profile.supports_reasoning,
            quality_threshold=params.quality_threshold,
            allow_long_responses=params.allow_long_responses
        )
        
        # Model-specific adjustments
        if 'qwen' in model_profile.name.lower() and task_type in [TaskType.MATHEMATICAL, TaskType.LOGICAL]:
            # Qwen3 is good at reasoning - allow thinking mode
            adjusted.enable_thinking_mode = True
            adjusted.max_new_tokens = min(200, model_profile.max_reliable_tokens)
        
        if 'phi' in model_profile.name.lower() and task_type == TaskType.MATHEMATICAL:
            # Phi-3 excels at math - maximize parameters
            adjusted.show_reasoning = True
            adjusted.max_new_tokens = min(250, model_profile.max_reliable_tokens)
        
        if 'dialogpt' in model_profile.name.lower() and task_type == TaskType.CONVERSATIONAL:
            # DialoGPT is conversational - allow longer responses
            adjusted.max_new_tokens = min(120, model_profile.max_reliable_tokens)
        
        return adjusted
    
    def _to_generation_config(self, params: AdaptiveParameters, model_profile: ModelProfile) -> Dict:
        """Convert AdaptiveParameters to generation config dictionary"""
        
        config = {
            'max_new_tokens': params.max_new_tokens,
            'do_sample': params.do_sample,
            'repetition_penalty': params.repetition_penalty,
            'no_repeat_ngram_size': params.no_repeat_ngram_size,
            'num_beams': params.num_beams
        }
        
        # Only add sampling parameters if sampling is enabled AND temperature exists
        if params.do_sample:
            if params.temperature is not None:
                config['temperature'] = params.temperature
            if params.top_p is not None:
                config['top_p'] = params.top_p
            if params.top_k is not None:
                config['top_k'] = params.top_k
        # For deterministic generation (do_sample=False), completely omit all sampling parameters
        # This prevents PyTorch warnings and ensures clean deterministic generation
        
        return config
    
    def update_quality_score(self, model_name: str, task_type: TaskType, 
                           quality_score: float, user_feedback: Optional[bool] = None):
        """Update quality tracking for adaptive learning"""
        
        key = f"{model_name.lower()}_{task_type.value}"
        
        if key not in self.quality_history:
            self.quality_history[key] = []
        
        self.quality_history[key].append({
            'quality': quality_score,
            'feedback': user_feedback,
            'timestamp': time.time()
        })
        
        # Keep only recent history (last 50 entries)
        if len(self.quality_history[key]) > 50:
            self.quality_history[key] = self.quality_history[key][-50:]
    
    def get_model_trust_score(self, model_name: str, task_type: TaskType) -> float:
        """Get current trust score for model on specific task type"""
        
        key = f"{model_name.lower()}_{task_type.value}"
        
        if key not in self.quality_history:
            # No history - return base model score
            model_profile = self.model_profiles.get(model_name.lower())
            return model_profile.base_quality_score if model_profile else 0.5
        
        recent_scores = [entry['quality'] for entry in self.quality_history[key][-10:]]
        return sum(recent_scores) / len(recent_scores) if recent_scores else 0.5

# Global instance for easy access
adaptive_config = AdaptiveModelConfig()

def get_adaptive_config(query: str, model_name: str, 
                       context: List[str] = None, 
                       user_confidence: Optional[float] = None) -> Dict:
    """
    Convenience function to get adaptive configuration
    
    Args:
        query: User's query
        model_name: Model identifier  
        context: Previous conversation context
        user_confidence: Optional confidence override
        
    Returns:
        Optimized generation parameters
    """
    return adaptive_config.get_optimal_config(query, model_name, context, user_confidence)

if __name__ == "__main__":
    # Test the adaptive configuration system
    test_cases = [
        ("What is 15 + 27?", "qwen/qwen3-0.6b"),
        ("Tell me a story about a robot", "microsoft/dialogpt-small"),
        ("Explain quantum physics step by step", "microsoft/phi-3-mini-4k-instruct"),
        ("Hello, how are you?", "tinyllama/tinyllama-1.1b-chat-v1.0"),
        ("Write Python code to sort a list", "microsoft/phi-3-mini-4k-instruct")
    ]
    
    print("🧪 Testing Adaptive Model Configuration")
    print("=" * 60)
    
    for query, model in test_cases:
        config = get_adaptive_config(query, model)
        metadata = config.pop('_metadata', {})
        
        print(f"\nQuery: \"{query}\"")
        print(f"Model: {model}")
        print(f"Task Type: {metadata.get('task_type')}")
        print(f"Confidence: {metadata.get('confidence_level')}")
        print(f"Thinking Mode: {metadata.get('thinking_mode_enabled')}")
        print(f"Max Tokens: {config.get('max_new_tokens')}")
        print(f"Temperature: {config.get('temperature')}")
        print(f"Parameters: {config}")