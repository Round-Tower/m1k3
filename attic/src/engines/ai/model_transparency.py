#!/usr/bin/env python3
"""
M1K3 Model Transparency Engine
Enhanced terminal output showing model processing details for development and transparency
"""

import time
import json
import re
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Any, Generator
from enum import Enum

class TransparencyLevel(Enum):
    """Transparency levels for development purposes"""
    OFF = 0          # No extra transparency (production mode)
    BASIC = 1        # Show basic model info (generation time, token count)
    DETAILED = 2     # Show parameters, confidence, task classification
    FULL = 3         # Show all internal processing (dev mode)
    DEBUG = 4        # Maximum transparency with raw outputs

@dataclass
class ModelDecision:
    """Tracks a model processing decision"""
    timestamp: float
    decision_type: str
    input_data: str
    output_data: str
    reasoning: Optional[str] = None
    confidence: Optional[float] = None
    metadata: Optional[Dict] = None

@dataclass
class ProcessingStats:
    """Statistics about model processing"""
    start_time: float
    end_time: float
    total_tokens: int
    tokens_per_second: float
    model_name: str
    backend_type: str
    parameters_used: Dict[str, Any]
    task_classification: Optional[str] = None
    confidence_score: Optional[float] = None
    thinking_detected: bool = False
    response_quality: Optional[str] = None

class ModelTransparencyEngine:
    """Provides transparency into model processing for development"""
    
    def __init__(self, transparency_level: TransparencyLevel = TransparencyLevel.BASIC):
        self.transparency_level = transparency_level
        self.decisions: List[ModelDecision] = []
        self.current_session_stats: List[ProcessingStats] = []
        self.enable_real_time_display = True
        self.force_debug_mode = False  # Override for maximum transparency
        
    def set_transparency_level(self, level: TransparencyLevel):
        """Change transparency level dynamically"""
        self.transparency_level = level
        if level == TransparencyLevel.OFF:
            self.enable_real_time_display = False
            self.force_debug_mode = False
        elif level == TransparencyLevel.DEBUG:
            self.enable_real_time_display = True
            self.force_debug_mode = True
            print("🔍 DEBUG MODE: Forcing all transparency output")
        else:
            self.enable_real_time_display = True
            self.force_debug_mode = False
    
    def set_debug_mode(self, force_all=True):
        """Force debug mode with maximum transparency"""
        self.transparency_level = TransparencyLevel.DEBUG
        self.enable_real_time_display = True
        self.force_debug_mode = force_all
        print(f"🔍 DEBUG MODE ACTIVATED: force_all={force_all}")
    
    def _should_show(self, required_level: TransparencyLevel) -> bool:
        """Check if we should show output based on level or debug mode"""
        if self.force_debug_mode:
            print(f"🔍 DEBUG: Force showing output (required: {required_level.name})")
            return True
        return self.transparency_level.value >= required_level.value
    
    def log_decision(self, decision_type: str, input_data: str, output_data: str, 
                    reasoning: str = None, confidence: float = None, metadata: Dict = None):
        """Log a model processing decision"""
        if self.transparency_level == TransparencyLevel.OFF:
            return
            
        decision = ModelDecision(
            timestamp=time.time(),
            decision_type=decision_type,
            input_data=input_data[:200] + "..." if len(input_data) > 200 else input_data,
            output_data=output_data[:200] + "..." if len(output_data) > 200 else output_data,
            reasoning=reasoning,
            confidence=confidence,
            metadata=metadata or {}
        )
        
        self.decisions.append(decision)
        
        # Real-time display for development
        if self.enable_real_time_display and self.transparency_level.value >= TransparencyLevel.DETAILED.value:
            self._display_decision(decision)
    
    def start_processing(self, model_name: str, backend_type: str, parameters: Dict) -> float:
        """Mark start of model processing"""
        start_time = time.time()
        
        if self._should_show(TransparencyLevel.BASIC):
            print(f"\n🔍 Starting processing with {model_name} ({backend_type})")
            
        if self._should_show(TransparencyLevel.DETAILED):
            print(f"📋 Parameters: {self._format_parameters(parameters)}")
            
        return start_time
    
    def end_processing(self, start_time: float, model_name: str, backend_type: str, 
                      parameters: Dict, total_tokens: int, task_classification: str = None,
                      confidence_score: float = None, thinking_detected: bool = False,
                      response_quality: str = None):
        """Mark end of processing and display stats"""
        end_time = time.time()
        duration = end_time - start_time
        tokens_per_sec = total_tokens / duration if duration > 0 else 0
        
        stats = ProcessingStats(
            start_time=start_time,
            end_time=end_time,
            total_tokens=total_tokens,
            tokens_per_second=tokens_per_sec,
            model_name=model_name,
            backend_type=backend_type,
            parameters_used=parameters,
            task_classification=task_classification,
            confidence_score=confidence_score,
            thinking_detected=thinking_detected,
            response_quality=response_quality
        )
        
        self.current_session_stats.append(stats)
        
        # Display processing results
        if self.transparency_level.value >= TransparencyLevel.BASIC.value:
            self._display_processing_stats(stats, duration)
    
    def show_model_reasoning(self, prompt: str, formatted_prompt: str, task_type: str = None, 
                           parameters: Dict = None):
        """Show how the model is processing the input"""
        if not self._should_show(TransparencyLevel.DETAILED):
            return
            
        print(f"\n🧠 Model Processing Analysis:")
        print(f"📝 User Input: '{prompt[:100]}{'...' if len(prompt) > 100 else ''}'")
        
        if task_type:
            print(f"🎯 Task Classification: {task_type}")
        
        if self._should_show(TransparencyLevel.FULL):
            print(f"🔧 Formatted Prompt Preview:")
            preview = formatted_prompt[:300] + "..." if len(formatted_prompt) > 300 else formatted_prompt
            print(f"/n {preview} /n")
            
        if parameters and self._should_show(TransparencyLevel.DETAILED):
            print(f"⚙️  Generation Parameters:")
            for key, value in parameters.items():
                if key not in ['pad_token_id', 'eos_token_id']:  # Skip technical IDs
                    print(f"   {key}: {value}")
    
    def show_streaming_progress(self, token_count: int, estimated_total: int, current_token: str = None):
        """Show real-time streaming progress"""
        if self.transparency_level.value < TransparencyLevel.DETAILED.value:
            return
            
        progress = (token_count / estimated_total * 100) if estimated_total > 0 else 0
        bar_length = 20
        filled_length = int(bar_length * progress / 100)
        bar = '█' * filled_length + '░' * (bar_length - filled_length)
        
        # Only show every 10 tokens to avoid spam
        if token_count % 10 == 0 or progress >= 95:
            status = f"⚡ Generating: [{bar}] {progress:.1f}% ({token_count}/{estimated_total} tokens)"
            if self.transparency_level.value >= TransparencyLevel.FULL.value and current_token:
                status += f" | Latest: '{current_token}'"
            print(f"\r{status}", end="", flush=True)
    
    def analyze_response_quality(self, response: str, thinking_content: str = None) -> Dict[str, Any]:
        """Analyze response quality and return insights"""
        if self.transparency_level.value < TransparencyLevel.DETAILED.value:
            return {}
            
        analysis = {
            "length": len(response),
            "word_count": len(response.split()),
            "sentence_count": len(re.findall(r'[.!?]+', response)),
            "has_thinking": bool(thinking_content),
            "complexity_score": self._calculate_complexity_score(response),
            "coherence_indicators": self._check_coherence(response)
        }
        
        if self.transparency_level.value >= TransparencyLevel.FULL.value:
            print(f"\n📊 Response Quality Analysis:")
            print(f"   Length: {analysis['length']} chars, {analysis['word_count']} words, {analysis['sentence_count']} sentences")
            print(f"   Complexity Score: {analysis['complexity_score']:.2f}/1.0")
            print(f"   Has Thinking Process: {'✅' if analysis['has_thinking'] else '❌'}")
            print(f"   Coherence Indicators: {len(analysis['coherence_indicators'])} found")
            
            if thinking_content and self.transparency_level == TransparencyLevel.DEBUG:
                print(f"🤔 Thinking Process Preview:")
                thinking_preview = thinking_content[:200] + "..." if len(thinking_content) > 200 else thinking_content
                print(f"   {thinking_preview}")
        
        return analysis
    
    def show_confidence_breakdown(self, confidence_score: float, factors: Dict[str, float] = None):
        """Show confidence score breakdown"""
        if self.transparency_level.value < TransparencyLevel.DETAILED.value:
            return
            
        print(f"\n🎯 Confidence Analysis:")
        print(f"   Overall Score: {confidence_score:.2f}/1.0 ({self._confidence_to_text(confidence_score)})")
        
        if factors and self.transparency_level.value >= TransparencyLevel.FULL.value:
            print(f"   Contributing Factors:")
            for factor, score in factors.items():
                print(f"     {factor}: {score:.2f}")
    
    def display_session_summary(self):
        """Display summary of current session transparency data"""
        if self.transparency_level == TransparencyLevel.OFF or not self.current_session_stats:
            return
            
        print(f"\n📈 Session Transparency Summary:")
        print(f"   Responses Generated: {len(self.current_session_stats)}")
        
        if self.current_session_stats:
            avg_tokens = sum(stat.total_tokens for stat in self.current_session_stats) / len(self.current_session_stats)
            avg_speed = sum(stat.tokens_per_second for stat in self.current_session_stats) / len(self.current_session_stats)
            
            print(f"   Average Response Length: {avg_tokens:.1f} tokens")
            print(f"   Average Generation Speed: {avg_speed:.1f} tokens/second")
            
            # Show model usage
            models_used = {}
            for stat in self.current_session_stats:
                models_used[stat.model_name] = models_used.get(stat.model_name, 0) + 1
            print(f"   Models Used: {', '.join([f'{model}({count})' for model, count in models_used.items()])}")
            
            # Show thinking detection rate
            thinking_count = sum(1 for stat in self.current_session_stats if stat.thinking_detected)
            if thinking_count > 0:
                print(f"   Thinking Process Detected: {thinking_count}/{len(self.current_session_stats)} responses ({thinking_count/len(self.current_session_stats)*100:.1f}%)")
    
    def export_transparency_data(self) -> Dict[str, Any]:
        """Export transparency data for analysis"""
        if self.transparency_level == TransparencyLevel.OFF:
            return {}
            
        return {
            "transparency_level": self.transparency_level.name,
            "session_stats": [asdict(stat) for stat in self.current_session_stats],
            "decisions": [asdict(decision) for decision in self.decisions],
            "summary": {
                "total_responses": len(self.current_session_stats),
                "total_decisions": len(self.decisions),
                "avg_tokens_per_response": sum(stat.total_tokens for stat in self.current_session_stats) / len(self.current_session_stats) if self.current_session_stats else 0
            }
        }
    
    def _display_decision(self, decision: ModelDecision):
        """Display a model decision in real-time"""
        print(f"🔍 [{decision.decision_type}] {decision.input_data} → {decision.output_data}")
        if decision.reasoning:
            print(f"   💭 Reasoning: {decision.reasoning}")
        if decision.confidence:
            print(f"   🎯 Confidence: {decision.confidence:.2f}")
    
    def _display_processing_stats(self, stats: ProcessingStats, duration: float):
        """Display processing statistics"""
        print(f"⏱️  Processing completed in {duration:.2f}s")
        print(f"📊 Generated {stats.total_tokens} tokens at {stats.tokens_per_second:.1f} tokens/sec")
        
        if self.transparency_level.value >= TransparencyLevel.DETAILED.value:
            if stats.task_classification:
                print(f"🎯 Task: {stats.task_classification}")
            if stats.confidence_score:
                print(f"🎯 Confidence: {stats.confidence_score:.2f} ({self._confidence_to_text(stats.confidence_score)})")
            if stats.response_quality:
                print(f"✨ Quality: {stats.response_quality}")
    
    def _format_parameters(self, parameters: Dict) -> str:
        """Format parameters for display"""
        formatted = []
        for key, value in parameters.items():
            if key in ['pad_token_id', 'eos_token_id']:
                continue  # Skip technical IDs
            if isinstance(value, float):
                formatted.append(f"{key}={value:.2f}")
            else:
                formatted.append(f"{key}={value}")
        return ", ".join(formatted[:5])  # Limit to 5 parameters for readability
    
    def _calculate_complexity_score(self, text: str) -> float:
        """Calculate text complexity score (0-1)"""
        if not text:
            return 0.0
            
        words = text.split()
        sentences = re.findall(r'[.!?]+', text)
        
        # Basic complexity indicators
        avg_word_length = sum(len(word) for word in words) / len(words) if words else 0
        avg_sentence_length = len(words) / len(sentences) if sentences else len(words)
        
        # Vocabulary diversity
        unique_words = len(set(word.lower() for word in words))
        diversity_ratio = unique_words / len(words) if words else 0
        
        # Technical terms indicator
        technical_patterns = [r'\b(function|algorithm|parameter|variable|system|process|method|approach)\b']
        technical_count = sum(len(re.findall(pattern, text, re.IGNORECASE)) for pattern in technical_patterns)
        
        # Combine factors
        complexity = (
            min(avg_word_length / 8, 1.0) * 0.3 +  # Word length component
            min(avg_sentence_length / 20, 1.0) * 0.3 +  # Sentence length component
            diversity_ratio * 0.3 +  # Vocabulary diversity
            min(technical_count / len(words) * 10, 1.0) * 0.1  # Technical content
        )
        
        return min(complexity, 1.0)
    
    def _check_coherence(self, text: str) -> List[str]:
        """Check for coherence indicators in text"""
        indicators = []
        
        # Transition words
        if re.search(r'\b(however|therefore|furthermore|moreover|additionally|consequently)\b', text, re.IGNORECASE):
            indicators.append("transition_words")
        
        # Logical structure
        if re.search(r'\b(first|second|finally|in conclusion|for example)\b', text, re.IGNORECASE):
            indicators.append("logical_structure")
        
        # Reference coherence
        if re.search(r'\b(this|that|these|those|it|they)\b', text, re.IGNORECASE):
            indicators.append("reference_coherence")
        
        return indicators
    
    def _confidence_to_text(self, confidence: float) -> str:
        """Convert confidence score to descriptive text"""
        if confidence >= 0.8:
            return "High"
        elif confidence >= 0.6:
            return "Medium"
        elif confidence >= 0.4:
            return "Low"
        else:
            return "Very Low"


# Global transparency engine instance
transparency_engine = ModelTransparencyEngine()

# Convenience functions for easy integration
def set_transparency_level(level: TransparencyLevel):
    """Set global transparency level"""
    transparency_engine.set_transparency_level(level)

def log_model_decision(decision_type: str, input_data: str, output_data: str, 
                      reasoning: str = None, confidence: float = None):
    """Log a model decision"""
    transparency_engine.log_decision(decision_type, input_data, output_data, reasoning, confidence)

def show_processing_start(model_name: str, backend_type: str, parameters: Dict) -> float:
    """Show processing start"""
    return transparency_engine.start_processing(model_name, backend_type, parameters)

def show_processing_end(start_time: float, model_name: str, backend_type: str, 
                       parameters: Dict, total_tokens: int, **kwargs):
    """Show processing end"""
    transparency_engine.end_processing(start_time, model_name, backend_type, 
                                     parameters, total_tokens, **kwargs)

def analyze_response(response: str, thinking_content: str = None) -> Dict[str, Any]:
    """Analyze response quality"""
    return transparency_engine.analyze_response_quality(response, thinking_content)

def show_session_summary():
    """Show session summary"""
    transparency_engine.display_session_summary()

if __name__ == "__main__":
    # Test the transparency engine
    print("Testing M1K3 Model Transparency Engine\n")
    
    # Test different transparency levels
    for level in [TransparencyLevel.BASIC, TransparencyLevel.DETAILED, TransparencyLevel.FULL]:
        print(f"=== Testing {level.name} Level ===")
        set_transparency_level(level)
        
        # Simulate model processing
        start_time = show_processing_start("TinyLlama-1.1B-Chat", "HuggingFace", {
            "max_new_tokens": 150,
            "temperature": 0.7,
            "do_sample": True
        })
        
        time.sleep(0.1)  # Simulate processing
        
        response = "This is a test response that demonstrates the transparency features of M1K3."
        analysis = analyze_response(response)
        
        show_processing_end(start_time, "TinyLlama-1.1B-Chat", "HuggingFace", {
            "max_new_tokens": 150,
            "temperature": 0.7
        }, 25, task_classification="conversational", confidence_score=0.75)
        
        print("\n" + "="*50 + "\n")
    
    show_session_summary()