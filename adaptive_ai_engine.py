#!/usr/bin/env python3
"""
M1K3 Adaptive AI Engine
Integrates ThinkingModeEngine with existing AI inference for intelligent response generation
"""

import time
from typing import Generator, Dict, Optional, Callable, Any
from dataclasses import dataclass

from thinking_mode_engine import ThinkingModeEngine, QueryComplexity
from thinking_parser import ThinkingContentParser, ReasoningType
from avatar_controller import AvatarController, AvatarState, AvatarEmotion
from adaptive_model_config import AdaptiveModelConfig, TaskType, ConfidenceLevel

@dataclass
class ResponseMetrics:
    """Metrics for response quality and performance"""
    complexity_detected: str
    mode_used: str
    thinking_enabled: bool
    response_time: float
    reasoning_quality: str
    confidence_score: float
    avatar_states_used: list
    token_count: int
    task_type: str = "unknown"
    adaptive_params_used: bool = False
    model_name: str = "unknown"

class AdaptiveAIEngine:
    """
    Intelligent AI engine that adapts response generation based on query complexity
    and leverages thinking modes for enhanced reasoning while maintaining quality
    """
    
    def __init__(self, base_ai_engine, avatar_controller: Optional[AvatarController] = None,
                 enable_thinking_mode: bool = True, show_reasoning_by_default: bool = False):
        self.base_ai_engine = base_ai_engine
        self.avatar_controller = avatar_controller
        self.enable_thinking_mode = enable_thinking_mode
        self.show_reasoning_by_default = show_reasoning_by_default
        
        # Initialize thinking components
        self.thinking_engine = ThinkingModeEngine(
            base_ai_engine, 
            avatar_callback=self._avatar_callback if avatar_controller else None
        )
        self.thinking_parser = ThinkingContentParser()
        
        # Initialize adaptive configuration
        self.adaptive_config = AdaptiveModelConfig()
        
        # User preferences
        self.user_preferences = {
            "thinking_mode": "adaptive",  # "always", "never", "adaptive"
            "show_reasoning": "auto",     # "always", "never", "auto"
            "complexity_threshold": "medium",  # "low", "medium", "high"
            "max_thinking_time": 30,      # seconds
        }
        
        # Performance tracking
        self.response_metrics = []
        self.total_responses = 0
        self.thinking_mode_successes = 0
        
    def _avatar_callback(self, callback_type: str, *args, **kwargs) -> None:
        """Handle avatar updates from thinking engine"""
        if not self.avatar_controller:
            return
            
        try:
            if callback_type == "state":
                state_name = args[0] if args else "thinking"
                avatar_state = getattr(AvatarState, state_name.upper(), AvatarState.THINKING)
                self.avatar_controller.update_state(avatar_state)
                
            elif callback_type == "emotion":
                emotion_name = args[0] if args else "thinking"
                intensity = args[1] if len(args) > 1 else 70
                message = args[2] if len(args) > 2 else ""
                
                avatar_emotion = getattr(AvatarEmotion, emotion_name.upper(), AvatarEmotion.THINKING)
                self.avatar_controller.update_emotion(message, "", force_emotion=avatar_emotion)
                
            elif callback_type == "progress":
                progress = args[0] if args else 0
                # Update avatar with progress information
                # This could be sent to the web dashboard for progress bars
                
        except Exception as e:
            print(f"Avatar callback error: {e}")
    
    def generate_response(self, query: str, max_tokens: int = 150, 
                         force_mode: Optional[str] = None,
                         show_reasoning: Optional[bool] = None) -> Generator[str, None, None]:
        """
        Generate adaptive response with intelligent mode selection
        """
        start_time = time.time()
        self.total_responses += 1
        
        # Get context for intelligent decision making
        context_messages = []
        if hasattr(self.base_ai_engine, 'context') and self.base_ai_engine.context:
            context_messages = [msg.get("content", "") for msg in getattr(self.base_ai_engine.context, 'messages', [])[-3:]]
        
        # Determine if thinking mode should be used
        use_thinking_mode = self._should_use_thinking_mode(query, force_mode, context_messages)
        
        # Determine if reasoning should be shown
        should_show_reasoning = self._should_show_reasoning(query, show_reasoning, use_thinking_mode)
        
        # Update avatar to show we're starting
        if self.avatar_controller:
            self.avatar_controller.update_state(AvatarState.PRE_THINKING)
            self.avatar_controller.update_emotion("", "", force_emotion=AvatarEmotion.THINKING)
        
        try:
            if use_thinking_mode:
                yield from self._generate_with_thinking(query, max_tokens, should_show_reasoning, start_time)
            else:
                yield from self._generate_direct(query, max_tokens, start_time)
                
        except Exception as e:
            # Fallback to direct mode on any thinking mode errors
            if use_thinking_mode:
                print(f"Thinking mode failed, falling back to direct: {e}")
                if self.avatar_controller:
                    self.avatar_controller.update_state(AvatarState.GENERATING)
                yield from self._generate_direct(query, max_tokens, start_time)
            else:
                if self.avatar_controller:
                    self.avatar_controller.update_state(AvatarState.ERROR)
                yield f"Error generating response: {e}"
    
    def _should_use_thinking_mode(self, query: str, force_mode: Optional[str] = None, context: list = None) -> bool:
        """Determine if thinking mode should be used for this query using intelligent task classification"""
        
        # Check user force mode
        if force_mode == "direct":
            return False
        elif force_mode == "thinking":
            return True
        
        # Check if thinking mode is disabled
        if not self.enable_thinking_mode:
            return False
        
        # Check user preferences
        if self.user_preferences["thinking_mode"] == "never":
            return False
        elif self.user_preferences["thinking_mode"] == "always":
            return True
        
        # Use intelligent task-aware thinking mode decision
        try:
            # Get context from previous responses if available
            context_messages = context or []
            if hasattr(self.base_ai_engine, 'context') and self.base_ai_engine.context:
                context_messages = [msg.get("content", "") for msg in getattr(self.base_ai_engine.context, 'messages', [])[-3:]]
            
            # Get adaptive configuration for this query
            model_name = getattr(self.base_ai_engine, 'current_model_name', 'unknown')
            adaptive_config = self.adaptive_config.get_optimal_config(
                query=query,
                model_name=model_name, 
                context=context_messages
            )
            
            # Extract metadata from adaptive config
            metadata = adaptive_config.get('_metadata', {})
            task_type_str = metadata.get('task_type', 'conversational')
            confidence_level_str = metadata.get('confidence_level', 'medium')
            
            # Convert strings to enums
            task_type = None
            confidence_level = None
            
            try:
                for tt in TaskType:
                    if tt.value == task_type_str:
                        task_type = tt
                        break
                if task_type is None:
                    task_type = TaskType.CONVERSATIONAL
                    
                for cl in ConfidenceLevel:
                    if cl.value == confidence_level_str:
                        confidence_level = cl
                        break
                if confidence_level is None:
                    confidence_level = ConfidenceLevel.MEDIUM
            except Exception as e:
                print(f"Error converting task type/confidence level: {e}")
                task_type = TaskType.CONVERSATIONAL
                confidence_level = ConfidenceLevel.MEDIUM
            
            # Enable thinking mode for tasks that benefit from reasoning
            thinking_beneficial_tasks = {
                TaskType.MATHEMATICAL,
                TaskType.LOGICAL, 
                TaskType.ANALYTICAL,
                TaskType.CODING
            }
            
            # Enable thinking mode based on task type and confidence
            if task_type in thinking_beneficial_tasks:
                return True
            
            # Enable for medium/high confidence complex queries
            if confidence_level in [ConfidenceLevel.HIGH, ConfidenceLevel.EXPERT]:
                if task_type in [TaskType.INSTRUCTIONAL, TaskType.CREATIVE]:
                    return True
            
            # Fallback to original complexity assessment
            complexity = self.thinking_engine.assess_query_complexity(query)
            threshold_map = {
                "low": QueryComplexity.SIMPLE,
                "medium": QueryComplexity.MODERATE,
                "high": QueryComplexity.COMPLEX
            }
            
            threshold = threshold_map.get(self.user_preferences["complexity_threshold"], QueryComplexity.MODERATE)
            complexity_order = [QueryComplexity.SIMPLE, QueryComplexity.MODERATE, QueryComplexity.COMPLEX]
            return complexity_order.index(complexity) >= complexity_order.index(threshold)
            
        except Exception as e:
            print(f"Adaptive thinking mode decision failed, using fallback: {e}")
            
            # Fallback to original logic
            complexity = self.thinking_engine.assess_query_complexity(query)
            threshold_map = {
                "low": QueryComplexity.SIMPLE,
                "medium": QueryComplexity.MODERATE,
                "high": QueryComplexity.COMPLEX
            }
            
            threshold = threshold_map.get(self.user_preferences["complexity_threshold"], QueryComplexity.MODERATE)
            complexity_order = [QueryComplexity.SIMPLE, QueryComplexity.MODERATE, QueryComplexity.COMPLEX]
            return complexity_order.index(complexity) >= complexity_order.index(threshold)
    
    def _should_show_reasoning(self, query: str, show_reasoning: Optional[bool], 
                             using_thinking_mode: bool) -> bool:
        """Determine if reasoning process should be shown to user"""
        
        # Explicit user request overrides everything
        if show_reasoning is not None:
            return show_reasoning
        
        # Can't show reasoning if not using thinking mode
        if not using_thinking_mode:
            return False
        
        # Check user preferences
        if self.user_preferences["show_reasoning"] == "always":
            return True
        elif self.user_preferences["show_reasoning"] == "never":
            return False
        
        # Auto mode - show reasoning for complex queries or when explicitly requested
        complexity = self.thinking_engine.assess_query_complexity(query)
        
        # Show reasoning only when explicitly requested or for very complex analysis
        query_lower = query.lower()
        
        # More precise pattern matching to avoid false positives
        reasoning_patterns = [
            r'\bexplain.*how\b',           # "explain how this works"
            r'\bshow.*work\b',             # "show your work" 
            r'\bstep by step\b',           # "step by step"
            r'\bhow did you\b',            # "how did you calculate"
            r'\bwhy.*because\b',           # "why is this because"
            r'\breasoning\b',              # explicit reasoning request
            r'\bthink through\b',          # "think through this"
            r'\bwalk.*through\b',          # "walk me through"
            r'\bbreak.*down\b',            # "break this down"
            r'\bshow me how\b'             # "show me how"
        ]
        
        import re
        explicit_reasoning_request = any(
            re.search(pattern, query_lower) for pattern in reasoning_patterns
        )
        
        # Only show reasoning if explicitly requested OR very complex query
        return (complexity == QueryComplexity.COMPLEX and explicit_reasoning_request)
    
    def _generate_with_thinking(self, query: str, max_tokens: int, 
                              show_reasoning: bool, start_time: float) -> Generator[str, None, None]:
        """Generate response using thinking mode"""
        
        response_text = ""
        avatar_states_used = []
        
        try:
            # Generate with thinking mode
            for token in self.thinking_engine.generate_with_thinking(
                query, max_tokens, show_reasoning=show_reasoning
            ):
                response_text += token
                yield token
                
                # Track avatar states
                if self.avatar_controller:
                    current_state = self.avatar_controller.current_state.value
                    if current_state not in avatar_states_used:
                        avatar_states_used.append(current_state)
            
            # Mark thinking mode as successful
            self.thinking_mode_successes += 1
            
            # Record metrics
            response_time = time.time() - start_time
            self._record_response_metrics(
                query, response_text, "thinking", True, response_time, 
                avatar_states_used, show_reasoning
            )
            
        except Exception as e:
            print(f"Thinking mode generation error: {e}")
            raise  # Let caller handle fallback
    
    def _generate_direct(self, query: str, max_tokens: int, start_time: float) -> Generator[str, None, None]:
        """Generate response using direct mode with adaptive parameter optimization"""
        
        response_text = ""
        
        if self.avatar_controller:
            self.avatar_controller.update_state(AvatarState.GENERATING)
            self.avatar_controller.update_emotion("", "", force_emotion=AvatarEmotion.HAPPY)
        
        # Use adaptive parameters if base engine supports them
        try:
            # Get context for adaptive configuration
            context_messages = []
            if hasattr(self.base_ai_engine, 'context') and self.base_ai_engine.context:
                context_messages = [msg.get("content", "") for msg in getattr(self.base_ai_engine.context, 'messages', [])[-3:]]
            
            # Use standard generation - adaptive params are handled internally by the AI engine
            for token in self.base_ai_engine.generate_response(query, max_tokens):
                response_text += token
                yield token
                    
        except Exception as e:
            print(f"Adaptive direct generation failed, using standard: {e}")
            # Fallback to standard generation
            for token in self.base_ai_engine.generate_response(query, max_tokens):
                response_text += token
                yield token
        
        # Record metrics
        response_time = time.time() - start_time
        avatar_states_used = [AvatarState.GENERATING.value] if self.avatar_controller else []
        
        self._record_response_metrics(
            query, response_text, "direct", False, response_time, 
            avatar_states_used, False
        )
    
    def _record_response_metrics(self, query: str, response: str, mode: str, 
                               thinking_enabled: bool, response_time: float,
                               avatar_states: list, reasoning_shown: bool):
        """Record metrics for analysis and improvement with adaptive insights"""
        
        complexity = self.thinking_engine.assess_query_complexity(query)
        
        # Get adaptive configuration insights
        task_type = "unknown"
        adaptive_params_used = False
        model_name = getattr(self.base_ai_engine, 'current_model_name', 'unknown')
        
        try:
            # Get context for adaptive analysis
            context_messages = []
            if hasattr(self.base_ai_engine, 'context') and self.base_ai_engine.context:
                context_messages = [msg.get("content", "") for msg in getattr(self.base_ai_engine.context, 'messages', [])[-3:]]
            
            adaptive_config = self.adaptive_config.get_optimal_config(
                query=query,
                model_name=model_name,
                context=context_messages
            )
            
            # Extract task type from metadata
            metadata = adaptive_config.get('_metadata', {})
            task_type = metadata.get('task_type', 'conversational')
            adaptive_params_used = True
            
        except Exception as e:
            print(f"Failed to get adaptive insights for metrics: {e}")
        
        # Analyze response quality if thinking was used
        reasoning_quality = "n/a"
        confidence_score = 0.7  # Default
        
        if thinking_enabled and "<think>" in response:
            try:
                thinking_flow = self.thinking_parser.parse_thinking_content(response)
                reasoning_quality = thinking_flow.final_confidence.value
                confidence_score = thinking_flow.overall_confidence
            except:
                reasoning_quality = "parse_error"
        
        metrics = ResponseMetrics(
            complexity_detected=complexity.value,
            mode_used=mode,
            thinking_enabled=thinking_enabled,
            response_time=response_time,
            reasoning_quality=reasoning_quality,
            confidence_score=confidence_score,
            avatar_states_used=avatar_states,
            token_count=len(response.split()),
            task_type=task_type,
            adaptive_params_used=adaptive_params_used,
            model_name=model_name
        )
        
        self.response_metrics.append(metrics)
        
        # Keep only last 100 metrics to avoid memory bloat
        if len(self.response_metrics) > 100:
            self.response_metrics = self.response_metrics[-100:]
    
    def set_user_preference(self, key: str, value: Any) -> bool:
        """Update user preference"""
        if key in self.user_preferences:
            self.user_preferences[key] = value
            return True
        return False
    
    def get_performance_stats(self) -> Dict[str, Any]:
        """Get performance statistics with adaptive insights"""
        if not self.response_metrics:
            return {"message": "No responses generated yet"}
        
        recent_metrics = self.response_metrics[-20:]  # Last 20 responses
        
        thinking_mode_usage = sum(1 for m in recent_metrics if m.thinking_enabled)
        adaptive_params_usage = sum(1 for m in recent_metrics if m.adaptive_params_used)
        avg_response_time = sum(m.response_time for m in recent_metrics) / len(recent_metrics)
        avg_confidence = sum(m.confidence_score for m in recent_metrics) / len(recent_metrics)
        
        complexity_counts = {}
        task_type_counts = {}
        for m in recent_metrics:
            complexity_counts[m.complexity_detected] = complexity_counts.get(m.complexity_detected, 0) + 1
            task_type_counts[m.task_type] = task_type_counts.get(m.task_type, 0) + 1
        
        # Calculate task-specific performance
        thinking_by_task = {}
        for m in recent_metrics:
            if m.task_type not in thinking_by_task:
                thinking_by_task[m.task_type] = {'total': 0, 'thinking_used': 0}
            thinking_by_task[m.task_type]['total'] += 1
            if m.thinking_enabled:
                thinking_by_task[m.task_type]['thinking_used'] += 1
        
        return {
            "total_responses": self.total_responses,
            "thinking_mode_usage_rate": thinking_mode_usage / len(recent_metrics) * 100,
            "adaptive_params_usage_rate": adaptive_params_usage / len(recent_metrics) * 100,
            "thinking_mode_success_rate": (self.thinking_mode_successes / max(1, self.total_responses)) * 100,
            "average_response_time": avg_response_time,
            "average_confidence": avg_confidence,
            "complexity_distribution": complexity_counts,
            "task_type_distribution": task_type_counts,
            "thinking_by_task_type": thinking_by_task,
            "user_preferences": self.user_preferences.copy()
        }
    
    def get_thinking_insights(self, query: str) -> Dict[str, Any]:
        """Get insights about how the engine would process a query"""
        base_insights = self.thinking_engine.get_thinking_insights(query)
        
        # Get context for analysis
        context_messages = []
        if hasattr(self.base_ai_engine, 'context') and self.base_ai_engine.context:
            context_messages = [msg.get("content", "") for msg in getattr(self.base_ai_engine.context, 'messages', [])[-3:]]
        
        would_use_thinking = self._should_use_thinking_mode(query, None, context_messages)
        would_show_reasoning = self._should_show_reasoning(query, None, would_use_thinking)
        
        # Get adaptive configuration insights
        adaptive_insights = {}
        try:
            model_name = getattr(self.base_ai_engine, 'current_model_name', 'unknown')
            adaptive_config = self.adaptive_config.get_optimal_config(
                query=query,
                model_name=model_name,
                context=context_messages
            )
            
            # Extract insights from metadata
            metadata = adaptive_config.get('_metadata', {})
            task_type_str = metadata.get('task_type', 'conversational')
            confidence_level_str = metadata.get('confidence_level', 'medium')
            
            # Check if reasoning is recommended
            reasoning_recommended = task_type_str in ['mathematical', 'logical', 'analytical', 'coding']
            
            adaptive_insights = {
                "adaptive_task_type": task_type_str,
                "adaptive_confidence_level": confidence_level_str,
                "adaptive_parameters": {k: v for k, v in adaptive_config.items() if k != '_metadata'},
                "reasoning_recommended": reasoning_recommended
            }
        except Exception as e:
            adaptive_insights = {"adaptive_error": str(e)}
        
        return {
            **base_insights,
            "would_use_thinking_mode": would_use_thinking,
            "would_show_reasoning": would_show_reasoning,
            "user_preferences": self.user_preferences.copy(),
            **adaptive_insights
        }
    
    def optimize_for_user(self) -> Dict[str, str]:
        """Auto-optimize settings based on user behavior patterns"""
        if len(self.response_metrics) < 10:
            return {"message": "Need more responses to optimize"}
        
        recent_metrics = self.response_metrics[-20:]
        
        # Analyze user patterns
        avg_thinking_time = sum(m.response_time for m in recent_metrics if m.thinking_enabled) / max(1, sum(1 for m in recent_metrics if m.thinking_enabled))
        
        recommendations = []
        
        # If thinking mode takes too long on average, suggest higher threshold
        if avg_thinking_time > self.user_preferences["max_thinking_time"]:
            recommendations.append("Consider raising complexity threshold to reduce thinking mode usage")
        
        # If thinking mode has high success rate, suggest more usage
        thinking_successes = sum(1 for m in recent_metrics if m.thinking_enabled and m.confidence_score > 0.7)
        thinking_total = sum(1 for m in recent_metrics if m.thinking_enabled)
        
        if thinking_total > 0 and thinking_successes / thinking_total > 0.8:
            recommendations.append("Thinking mode shows high success rate - consider lowering threshold")
        
        return {
            "recommendations": recommendations,
            "current_settings": self.user_preferences.copy(),
            "performance_summary": self.get_performance_stats()
        }

# Factory function for easy integration
def create_adaptive_engine(base_ai_engine, avatar_controller=None, **kwargs):
    """Create adaptive AI engine with default settings"""
    return AdaptiveAIEngine(base_ai_engine, avatar_controller, **kwargs)