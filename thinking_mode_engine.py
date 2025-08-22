#!/usr/bin/env python3
"""
M1K3 Thinking Mode Engine
Intelligently leverages model thinking capabilities while maintaining quality control
"""

import re
import time
from typing import Dict, List, Tuple, Optional, Generator, Callable
from dataclasses import dataclass
from enum import Enum
from thinking_parser import ThinkingFlow as ThinkingAnalysis  # Use unified structure

class QueryComplexity(Enum):
    SIMPLE = "simple"          # Basic facts, greetings - use direct mode
    MODERATE = "moderate"      # Short reasoning - lightweight thinking
    COMPLEX = "complex"        # Multi-step reasoning - full thinking mode


# ThinkingAnalysis is now imported as alias for ThinkingFlow from thinking_parser

class ThinkingModeEngine:
    """
    Dual-pass generation engine that leverages thinking modes intelligently
    """
    
    def __init__(self, base_ai_engine, avatar_callback: Optional[Callable] = None):
        self.base_ai_engine = base_ai_engine
        self.avatar_callback = avatar_callback  # For sending avatar updates
        
        # Thinking mode configuration (compatible with all models)
        self.thinking_params = {
            'max_new_tokens': 200,
            'do_sample': False,  # Use greedy decoding for consistency
            'repetition_penalty': 1.1,
        }
        
        # Direct mode configuration (current optimized params)
        self.direct_params = {
            'max_new_tokens': 30,
            'do_sample': False,
            'repetition_penalty': 1.1,
        }
        
        # Query complexity patterns
        self.complexity_indicators = {
            QueryComplexity.SIMPLE: [
                r'\b(hello|hi|hey|thanks|ok|yes|no)\b',
                r'\b(what is|who is|when is|where is)\s+\w+\s*\?',
                r'\b(capital of|define|meaning of)\b',
            ],
            QueryComplexity.COMPLEX: [
                r'\b(explain|analyze|compare|evaluate|reason|justify)\b',
                r'\b(why|how|what if|suppose|imagine)\b.*\?',
                r'\b(step by step|think through|work through)\b',
                r'\b(multiple|several|various|different)\b.*\b(ways|methods|approaches)\b',
                r'\d+.*[+\-*/].*\d+.*[+\-*/]',  # Multi-step math
            ]
        }
    
    def assess_query_complexity(self, query: str) -> QueryComplexity:
        """Determine if query needs thinking mode based on content analysis"""
        query_lower = query.lower()
        
        # Check for simple patterns first
        for pattern in self.complexity_indicators[QueryComplexity.SIMPLE]:
            if re.search(pattern, query_lower):
                return QueryComplexity.SIMPLE
        
        # Check for complex patterns
        for pattern in self.complexity_indicators[QueryComplexity.COMPLEX]:
            if re.search(pattern, query_lower):
                return QueryComplexity.COMPLEX
        
        # Default to moderate for everything else
        return QueryComplexity.MODERATE
    
    def generate_with_thinking(self, query: str, max_tokens: int = 150, 
                              force_mode: Optional[QueryComplexity] = None,
                              show_reasoning: bool = False) -> Generator[str, None, None]:
        """
        Generate response using adaptive thinking modes
        """
        
        # Determine complexity and mode
        complexity = force_mode or self.assess_query_complexity(query)
        
        if complexity == QueryComplexity.SIMPLE:
            # Use direct mode for simple queries
            yield from self._generate_direct(query, max_tokens)
        else:
            # Use thinking mode for moderate/complex queries
            yield from self._generate_with_thinking_mode(query, max_tokens, complexity, show_reasoning)
    
    def _generate_direct(self, query: str, max_tokens: int) -> Generator[str, None, None]:
        """Generate using current optimized direct parameters"""
        
        if self.avatar_callback:
            self.avatar_callback("state", "generating")
            self.avatar_callback("emotion", "focused", 70)
        
        # Use current optimized parameters for simple queries
        for token in self.base_ai_engine.generate_response(query, max_tokens):
            yield token
    
    def _generate_with_thinking_mode(self, query: str, max_tokens: int, 
                                   complexity: QueryComplexity, show_reasoning: bool) -> Generator[str, None, None]:
        """Generate using thinking mode with dual-pass approach"""
        
        # Phase 1: Generate with thinking enabled
        if self.avatar_callback:
            self.avatar_callback("state", "pre_thinking")
            self.avatar_callback("emotion", "thinking", 80)
        
        # Create thinking-friendly prompt
        thinking_prompt = self._create_thinking_prompt(query, complexity)
        
        # Temporarily override AI engine parameters for thinking mode
        original_params = self._override_ai_params(self.thinking_params)
        
        try:
            thinking_response = ""
            
            # Generate raw thinking response
            if self.avatar_callback:
                self.avatar_callback("state", "thinking")
            
            for token in self.base_ai_engine.generate_response(thinking_prompt, max_tokens):
                thinking_response += token
                # Don't yield during thinking phase
            
            # Phase 2: Analyze thinking and extract final answer
            if self.avatar_callback:
                self.avatar_callback("state", "generating")
                self.avatar_callback("emotion", "focused", 85)
            
            analysis = self._analyze_thinking_content(thinking_response, query)
            
            # Send thinking progress updates to avatar
            if self.avatar_callback and analysis.insights:
                for i, insight in enumerate(analysis.insights):
                    progress = (i + 1) / len(analysis.insights) * 100
                    self.avatar_callback("progress", progress)
                    if insight.emotion:
                        self.avatar_callback("emotion", insight.emotion, insight.confidence * 100)
            
            # Decide what to output - respect the explicit show_reasoning parameter
            if show_reasoning:
                # Show reasoning process when explicitly requested
                yield from self._format_reasoning_output(analysis)
            else:
                # Just show final answer (default behavior)
                yield from self._stream_final_answer(analysis.final_answer)
        
        finally:
            # Restore original AI parameters
            self._restore_ai_params(original_params)
    
    def _create_thinking_prompt(self, query: str, complexity: QueryComplexity) -> str:
        """Create prompt that encourages good thinking behavior"""
        
        if complexity == QueryComplexity.COMPLEX:
            system_msg = """Think through this step by step. Use <think> tags to show your reasoning process, then provide a clear final answer.

<think>
Let me analyze this question carefully...
</think>

Final answer: [your conclusion]"""
        else:
            system_msg = """Consider this question and provide a thoughtful response. You may use <think> tags for brief reasoning if needed."""
        
        return f"{system_msg}\n\nUser: {query}\n\nAssistant:"
    
    def _analyze_thinking_content(self, thinking_response: str, original_query: str) -> ThinkingAnalysis:
        """Parse thinking content and extract insights using unified parser"""
        
        # Use the unified ThinkingContentParser
        from thinking_parser import ThinkingContentParser
        parser = ThinkingContentParser()
        
        # Parse the thinking content - this returns a ThinkingFlow (aliased as ThinkingAnalysis)
        analysis = parser.parse_thinking_content(thinking_response)
        
        return analysis
    
    
    def _format_reasoning_output(self, analysis: ThinkingAnalysis) -> Generator[str, None, None]:
        """Format reasoning for display to user"""
        
        # Show brief reasoning summary
        yield "💭 Thinking through this...\n\n"
        
        for insight in analysis.insights:
            reasoning_emoji = {
                "mathematical": "🧮",
                "logical": "🧠",
                "creative": "✨",
                "analytical": "🔍",
                "conversational": "💬"
            }.get(insight.reasoning_type.value if hasattr(insight.reasoning_type, 'value') else str(insight.reasoning_type), "💭")
            
            yield f"{reasoning_emoji} {insight.content[:100]}...\n"
            time.sleep(0.3)  # Simulate thinking time
        
        yield f"\n💡 **Answer:** {analysis.final_answer}"
    
    def _stream_final_answer(self, final_answer: str) -> Generator[str, None, None]:
        """Stream just the final answer with word-by-word animation"""
        
        words = final_answer.split()
        for i, word in enumerate(words):
            if i > 0:
                yield " "
            yield word
            time.sleep(0.04)  # Current M1K3 streaming speed
    
    def _override_ai_params(self, new_params: Dict) -> Dict:
        """Temporarily override AI engine parameters"""
        # This would need to integrate with the actual AI engine parameter system
        # For now, return empty dict as placeholder
        return {}
    
    def _restore_ai_params(self, original_params: Dict):
        """Restore original AI engine parameters"""
        # Placeholder for parameter restoration
        pass
    
    def get_thinking_insights(self, query: str) -> Dict:
        """Get insights about how the engine would handle a query"""
        complexity = self.assess_query_complexity(query)
        
        return {
            "complexity": complexity.value,
            "recommended_mode": "thinking" if complexity != QueryComplexity.SIMPLE else "direct",
            "estimated_thinking_steps": 3 if complexity == QueryComplexity.COMPLEX else 1,
            "avatar_states": ["pre_thinking", "thinking", "generating"] if complexity != QueryComplexity.SIMPLE else ["generating"]
        }

# Convenience functions for M1K3 integration
def create_thinking_engine(ai_engine, avatar_callback=None):
    """Factory function to create thinking engine"""
    return ThinkingModeEngine(ai_engine, avatar_callback)

def assess_query_complexity(query: str) -> str:
    """Quick complexity assessment"""
    engine = ThinkingModeEngine(None)
    return engine.assess_query_complexity(query).value