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
        
        # Determine if thinking mode should be used
        use_thinking_mode = self._should_use_thinking_mode(query, force_mode)
        
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
    
    def _should_use_thinking_mode(self, query: str, force_mode: Optional[str]) -> bool:
        """Determine if thinking mode should be used for this query"""
        
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
        
        # Adaptive mode - assess query complexity
        complexity = self.thinking_engine.assess_query_complexity(query)
        
        threshold_map = {
            "low": QueryComplexity.SIMPLE,
            "medium": QueryComplexity.MODERATE,
            "high": QueryComplexity.COMPLEX
        }
        
        threshold = threshold_map.get(self.user_preferences["complexity_threshold"], QueryComplexity.MODERATE)
        
        # Use thinking mode for queries at or above threshold
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
        
        # Show reasoning for complex queries or when user asks for explanation
        return (complexity == QueryComplexity.COMPLEX or 
                any(phrase in query.lower() for phrase in [
                    "explain", "show your work", "step by step", "how did you",
                    "why", "reasoning", "think through"
                ]))
    
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
        """Generate response using direct mode"""
        
        response_text = ""
        
        if self.avatar_controller:
            self.avatar_controller.update_state(AvatarState.GENERATING)
            self.avatar_controller.update_emotion("", "", force_emotion=AvatarEmotion.HAPPY)
        
        # Generate using current optimized direct approach
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
        """Record metrics for analysis and improvement"""
        
        complexity = self.thinking_engine.assess_query_complexity(query)
        
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
            token_count=len(response.split())
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
        """Get performance statistics"""
        if not self.response_metrics:
            return {"message": "No responses generated yet"}
        
        recent_metrics = self.response_metrics[-20:]  # Last 20 responses
        
        thinking_mode_usage = sum(1 for m in recent_metrics if m.thinking_enabled)
        avg_response_time = sum(m.response_time for m in recent_metrics) / len(recent_metrics)
        avg_confidence = sum(m.confidence_score for m in recent_metrics) / len(recent_metrics)
        
        complexity_counts = {}
        for m in recent_metrics:
            complexity_counts[m.complexity_detected] = complexity_counts.get(m.complexity_detected, 0) + 1
        
        return {
            "total_responses": self.total_responses,
            "thinking_mode_usage_rate": thinking_mode_usage / len(recent_metrics) * 100,
            "thinking_mode_success_rate": (self.thinking_mode_successes / max(1, self.total_responses)) * 100,
            "average_response_time": avg_response_time,
            "average_confidence": avg_confidence,
            "complexity_distribution": complexity_counts,
            "user_preferences": self.user_preferences.copy()
        }
    
    def get_thinking_insights(self, query: str) -> Dict[str, Any]:
        """Get insights about how the engine would process a query"""
        base_insights = self.thinking_engine.get_thinking_insights(query)
        
        would_use_thinking = self._should_use_thinking_mode(query, None)
        would_show_reasoning = self._should_show_reasoning(query, None, would_use_thinking)
        
        return {
            **base_insights,
            "would_use_thinking_mode": would_use_thinking,
            "would_show_reasoning": would_show_reasoning,
            "user_preferences": self.user_preferences.copy()
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