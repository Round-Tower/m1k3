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

class QueryComplexity(Enum):
    SIMPLE = "simple"          # Basic facts, greetings - use direct mode
    MODERATE = "moderate"      # Short reasoning - lightweight thinking
    COMPLEX = "complex"        # Multi-step reasoning - full thinking mode

class ThinkingPhase(Enum):
    ANALYZING = "analyzing"
    REASONING = "reasoning"  
    CALCULATING = "calculating"
    SYNTHESIZING = "synthesizing"
    CONCLUDING = "concluding"

@dataclass
class ThinkingStep:
    phase: ThinkingPhase
    content: str
    confidence: float
    emotion_indicator: str
    progress: float  # 0.0 to 1.0

@dataclass
class ThinkingAnalysis:
    steps: List[ThinkingStep]
    final_answer: str
    confidence_score: float
    reasoning_quality: str  # "excellent", "good", "fair", "poor"
    should_show_reasoning: bool
    emotion_progression: List[str]

class ThinkingModeEngine:
    """
    Dual-pass generation engine that leverages thinking modes intelligently
    """
    
    def __init__(self, base_ai_engine, avatar_callback: Optional[Callable] = None):
        self.base_ai_engine = base_ai_engine
        self.avatar_callback = avatar_callback  # For sending avatar updates
        
        # Thinking mode configuration
        self.thinking_params = {
            'max_new_tokens': 200,
            'temperature': 0.6,
            'top_p': 0.9,
            'do_sample': True,
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
        
        # Thinking phase detection patterns
        self.phase_patterns = {
            ThinkingPhase.ANALYZING: [r'let me analyze', r'looking at', r'examining', r'considering'],
            ThinkingPhase.REASONING: [r'because', r'therefore', r'since', r'given that', r'this means'],
            ThinkingPhase.CALCULATING: [r'calculating', r'adding', r'subtracting', r'multiplying', r'equals'],
            ThinkingPhase.SYNTHESIZING: [r'putting together', r'combining', r'overall', r'in summary'],
            ThinkingPhase.CONCLUDING: [r'so the answer', r'therefore', r'in conclusion', r'final answer'],
        }
        
        # Emotion indicators in thinking
        self.emotion_indicators = {
            'confident': [r'clearly', r'obviously', r'definitely', r'certain'],
            'uncertain': [r'maybe', r'perhaps', r'might be', r'could be', r'not sure'],
            'excited': [r'interesting', r'fascinating', r'great question', r'excellent'],
            'thinking': [r'hmm', r'let me think', r'considering', r'analyzing'],
            'focused': [r'step by step', r'methodically', r'systematically'],
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
            if self.avatar_callback and analysis.steps:
                for i, step in enumerate(analysis.steps):
                    progress = (i + 1) / len(analysis.steps) * 100
                    self.avatar_callback("progress", progress)
                    if step.emotion_indicator:
                        self.avatar_callback("emotion", step.emotion_indicator, step.confidence * 100)
            
            # Decide what to output
            if show_reasoning and analysis.should_show_reasoning:
                # Show reasoning process
                yield from self._format_reasoning_output(analysis)
            else:
                # Just show final answer
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
        """Parse thinking content and extract insights"""
        
        # Extract thinking blocks
        think_pattern = r'<think>(.*?)</think>'
        think_matches = re.findall(think_pattern, thinking_response, re.DOTALL)
        
        # Extract final answer (everything after last </think> or whole response if no think tags)
        if think_matches:
            final_answer = re.split(r'</think>', thinking_response)[-1].strip()
        else:
            final_answer = thinking_response.strip()
        
        # Clean up final answer
        final_answer = re.sub(r'^(final answer:\s*|answer:\s*)', '', final_answer, flags=re.IGNORECASE)
        final_answer = final_answer.strip()
        
        # Analyze thinking steps
        steps = []
        emotion_progression = []
        overall_confidence = 0.7  # Default
        
        for i, think_content in enumerate(think_matches):
            step = self._analyze_thinking_step(think_content, i, len(think_matches))
            steps.append(step)
            emotion_progression.append(step.emotion_indicator)
            overall_confidence = (overall_confidence + step.confidence) / 2
        
        # Determine reasoning quality
        reasoning_quality = self._assess_reasoning_quality(steps, final_answer, original_query)
        
        # Decide if reasoning should be shown
        should_show_reasoning = (
            len(steps) > 1 and 
            reasoning_quality in ['excellent', 'good'] and
            len(final_answer) > 10
        )
        
        return ThinkingAnalysis(
            steps=steps,
            final_answer=final_answer,
            confidence_score=overall_confidence,
            reasoning_quality=reasoning_quality,
            should_show_reasoning=should_show_reasoning,
            emotion_progression=emotion_progression
        )
    
    def _analyze_thinking_step(self, content: str, step_index: int, total_steps: int) -> ThinkingStep:
        """Analyze individual thinking step"""
        
        content_lower = content.lower()
        
        # Detect phase
        phase = ThinkingPhase.REASONING  # Default
        for phase_type, patterns in self.phase_patterns.items():
            if any(re.search(pattern, content_lower) for pattern in patterns):
                phase = phase_type
                break
        
        # Detect emotion indicators
        emotion = "thinking"  # Default
        for emotion_type, patterns in self.emotion_indicators.items():
            if any(re.search(pattern, content_lower) for pattern in patterns):
                emotion = emotion_type
                break
        
        # Assess confidence based on language
        confidence = 0.7  # Default
        if any(word in content_lower for word in ['clearly', 'obviously', 'definitely']):
            confidence = 0.9
        elif any(word in content_lower for word in ['maybe', 'perhaps', 'might']):
            confidence = 0.4
        elif any(word in content_lower for word in ['probably', 'likely']):
            confidence = 0.6
        
        # Calculate progress
        progress = (step_index + 1) / total_steps
        
        return ThinkingStep(
            phase=phase,
            content=content.strip(),
            confidence=confidence,
            emotion_indicator=emotion,
            progress=progress
        )
    
    def _assess_reasoning_quality(self, steps: List[ThinkingStep], final_answer: str, query: str) -> str:
        """Assess overall quality of reasoning process"""
        
        if not steps:
            return "fair"  # No thinking steps
        
        # Check for logical progression
        has_analysis = any(step.phase == ThinkingPhase.ANALYZING for step in steps)
        has_reasoning = any(step.phase == ThinkingPhase.REASONING for step in steps)
        has_conclusion = any(step.phase == ThinkingPhase.CONCLUDING for step in steps)
        
        # Check answer quality
        answer_length_ok = len(final_answer.strip()) > 5
        answer_coherent = not any(indicator in final_answer.lower() 
                                for indicator in ['wait', 'hmm', 'let me think'])
        
        # Calculate quality score
        quality_score = 0
        if has_analysis: quality_score += 1
        if has_reasoning: quality_score += 1
        if has_conclusion: quality_score += 1
        if answer_length_ok: quality_score += 1
        if answer_coherent: quality_score += 1
        if len(steps) >= 2: quality_score += 1  # Multi-step reasoning
        
        # Map score to quality
        if quality_score >= 5:
            return "excellent"
        elif quality_score >= 3:
            return "good"
        elif quality_score >= 2:
            return "fair"
        else:
            return "poor"
    
    def _format_reasoning_output(self, analysis: ThinkingAnalysis) -> Generator[str, None, None]:
        """Format reasoning for display to user"""
        
        # Show brief reasoning summary
        yield "💭 Thinking through this...\n\n"
        
        for step in analysis.steps:
            phase_emoji = {
                ThinkingPhase.ANALYZING: "🔍",
                ThinkingPhase.REASONING: "🧠", 
                ThinkingPhase.CALCULATING: "🧮",
                ThinkingPhase.SYNTHESIZING: "🔗",
                ThinkingPhase.CONCLUDING: "✅"
            }.get(step.phase, "💭")
            
            yield f"{phase_emoji} {step.content[:100]}...\n"
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