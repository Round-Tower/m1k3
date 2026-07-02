#!/usr/bin/env python3
"""
M1K3 Avatar Controller
Manages avatar state and emotion mapping from AI responses
"""

import re
import time
from typing import Optional, Dict, Any, List
from enum import Enum

# Import enhanced classification system if available
try:
    from src.utils.intent_classification_system import UserIntent, ResponseStrategy, IntentClassification
    ENHANCED_CLASSIFICATION_AVAILABLE = True
except ImportError:
    ENHANCED_CLASSIFICATION_AVAILABLE = False

class AvatarEmotion(Enum):
    """Avatar emotions that map to pixel expressions"""
    HAPPY = "happy"
    SAD = "sad"
    ANGRY = "angry"
    SURPRISED = "surprised"
    LOVE = "love"
    THINKING = "thinking"
    SLEEPY = "sleepy"
    EXCITED = "excited"

class AvatarState(Enum):
    """Avatar states that reflect AI activity"""
    IDLE = "idle"
    PRE_THINKING = "pre_thinking" 
    THINKING = "thinking"
    ANALYZING = "analyzing"
    REASONING = "reasoning"
    CALCULATING = "calculating"
    SYNTHESIZING = "synthesizing"
    CONCLUDING = "concluding"
    GENERATING = "generating"
    SPEAKING = "speaking"
    ERROR = "error"
    LOADING = "loading"

class AvatarStyle(Enum):
    """Avatar visual styles"""
    ROBOT = "robot"
    ORGANIC = "organic"
    CRYSTAL = "crystal"
    GHOST = "ghost"
    ENERGY = "energy"
    CUTE = "cute"

class AvatarController:
    """Controls avatar appearance and emotions based on AI interactions"""
    
    def __init__(self):
        self.current_emotion = AvatarEmotion.HAPPY
        self.current_state = AvatarState.IDLE
        self.current_style = AvatarStyle.ROBOT
        self.current_color = "#E25303"  # M1K3 orange
        self.emotion_intensity = 50
        self.last_update = time.time()
        
        # Emotion detection patterns
        self.emotion_patterns = {
            AvatarEmotion.HAPPY: [
                r'\b(great|good|excellent|wonderful|fantastic|amazing|awesome|perfect|glad|pleased|happy|joy)\b',
                r'\b(thanks|thank you|appreciate)\b',
                r'[!]{1,3}$',  # Excitement
                r'😊|😄|😃|🙂|😌'
            ],
            AvatarEmotion.EXCITED: [
                r'\b(exciting|incredible|wow|amazing|fantastic|awesome|brilliant|outstanding)\b',
                r'[!]{2,}',  # Multiple exclamation marks
                r'🤩|🎉|✨|🚀'
            ],
            AvatarEmotion.THINKING: [
                r'\b(think|consider|analyze|evaluate|examine|understand|wondering|hmm|let me)\b',
                r'\b(however|although|perhaps|maybe|possibly|might|could)\b',
                r'🤔'
            ],
            AvatarEmotion.SURPRISED: [
                r'\b(wow|whoa|oh|unexpected|surprise|incredible|unbelievable)\b',
                r'[?!]{2,}',
                r'😲|😮|😯'
            ],
            AvatarEmotion.LOVE: [
                r'\b(love|adore|treasure|cherish|appreciate|beautiful|lovely)\b',
                r'❤️|💕|💖|😍|🥰'
            ],
            AvatarEmotion.SAD: [
                r'\b(sorry|unfortunately|sad|regret|disappointed|problem|error|failed|issue)\b',
                r'\b(can\'t|cannot|unable|impossible|difficult)\b',
                r'😢|😔|😞|😟|☹️'
            ],
            AvatarEmotion.ANGRY: [
                r'\b(angry|annoyed|frustrated|irritated|mad|upset)\b',
                r'😠|😡|🤬'
            ],
            AvatarEmotion.SLEEPY: [
                r'\b(tired|sleepy|exhausted|drowsy|fatigue)\b',
                r'😴|💤'
            ]
        }
        
        # Context-based emotion modifiers
        self.context_modifiers = {
            'greeting': AvatarEmotion.HAPPY,
            'farewell': AvatarEmotion.HAPPY,
            'error': AvatarEmotion.SAD,
            'success': AvatarEmotion.EXCITED,
            'processing': AvatarEmotion.THINKING,
            'helping': AvatarEmotion.HAPPY
        }
    
    def analyze_emotion_from_text(self, text: str, context: str = "") -> AvatarEmotion:
        """Analyze text and return the most appropriate emotion"""
        if not text:
            return AvatarEmotion.THINKING
        
        text_lower = text.lower()
        emotion_scores = {}
        
        # Score each emotion based on pattern matches
        for emotion, patterns in self.emotion_patterns.items():
            score = 0
            for pattern in patterns:
                matches = len(re.findall(pattern, text_lower, re.IGNORECASE))
                score += matches
            emotion_scores[emotion] = score
        
        # Apply context modifiers
        if context and context in self.context_modifiers:
            context_emotion = self.context_modifiers[context]
            emotion_scores[context_emotion] = emotion_scores.get(context_emotion, 0) + 2
        
        # Find the emotion with the highest score
        if any(score > 0 for score in emotion_scores.values()):
            best_emotion = max(emotion_scores.items(), key=lambda x: x[1])[0]
            return best_emotion
        
        # Default emotions based on context
        if context == 'error':
            return AvatarEmotion.SAD
        elif context == 'processing':
            return AvatarEmotion.THINKING
        elif context == 'greeting':
            return AvatarEmotion.HAPPY
        else:
            return AvatarEmotion.HAPPY  # Default to happy
    
    def calculate_emotion_intensity(self, text: str, emotion: AvatarEmotion) -> int:
        """Calculate emotion intensity (0-100) based on text analysis"""
        if not text:
            return 50
        
        intensity = 30  # Base intensity
        
        # Length factor (longer text = more intensity)
        length_factor = min(len(text) / 100, 1.0) * 20
        intensity += length_factor
        
        # Punctuation factor
        exclamations = text.count('!')
        questions = text.count('?')
        intensity += min(exclamations * 10, 30)
        intensity += min(questions * 5, 15)
        
        # Caps factor (uppercase words indicate intensity)
        caps_words = len([word for word in text.split() if word.isupper() and len(word) > 1])
        intensity += min(caps_words * 5, 20)
        
        # Emoji factor
        emoji_count = len(re.findall(r'[😀-🙏🚀-🛿]', text))
        intensity += min(emoji_count * 10, 20)
        
        return min(max(int(intensity), 10), 100)
    
    def update_emotion(self, text: str, context: str = "", force_emotion: Optional[AvatarEmotion] = None) -> Dict[str, Any]:
        """Update avatar emotion based on text analysis or forced emotion"""
        if force_emotion:
            new_emotion = force_emotion
            intensity = 70  # Higher intensity for forced emotions
        else:
            new_emotion = self.analyze_emotion_from_text(text, context)
            intensity = self.calculate_emotion_intensity(text, new_emotion)
        
        # Update state
        previous_emotion = self.current_emotion
        self.current_emotion = new_emotion
        self.emotion_intensity = intensity
        self.last_update = time.time()
        
        # Return update information
        return {
            "emotion": new_emotion.value,
            "intensity": intensity,
            "previous_emotion": previous_emotion.value,
            "changed": new_emotion != previous_emotion,
            "text": text,
            "context": context,
            "timestamp": self.last_update
        }
    
    def update_state(self, new_state: AvatarState) -> Dict[str, Any]:
        """Update avatar state"""
        previous_state = self.current_state
        self.current_state = new_state
        self.last_update = time.time()
        
        # State-specific emotion adjustments
        state_emotions = {
            AvatarState.PRE_THINKING: AvatarEmotion.THINKING,
            AvatarState.THINKING: AvatarEmotion.THINKING,
            AvatarState.ANALYZING: AvatarEmotion.THINKING,
            AvatarState.REASONING: AvatarEmotion.THINKING,
            AvatarState.CALCULATING: AvatarEmotion.THINKING,
            AvatarState.SYNTHESIZING: AvatarEmotion.THINKING,
            AvatarState.CONCLUDING: AvatarEmotion.EXCITED,
            AvatarState.LOADING: AvatarEmotion.THINKING,
            AvatarState.ERROR: AvatarEmotion.SAD,
            AvatarState.IDLE: AvatarEmotion.SLEEPY
        }
        
        if new_state in state_emotions:
            state_emotion = state_emotions[new_state]
            if self.current_emotion != state_emotion:
                self.current_emotion = state_emotion
        
        return {
            "state": new_state.value,
            "previous_state": previous_state.value,
            "changed": new_state != previous_state,
            "emotion": self.current_emotion.value,
            "timestamp": self.last_update
        }
    
    def update_style(self, new_style: AvatarStyle, color: Optional[str] = None) -> Dict[str, Any]:
        """Update avatar visual style"""
        previous_style = self.current_style
        self.current_style = new_style
        
        if color:
            self.current_color = color
        
        self.last_update = time.time()
        
        return {
            "style": new_style.value,
            "color": self.current_color,
            "previous_style": previous_style.value,
            "changed": new_style != previous_style,
            "timestamp": self.last_update
        }
    
    def update_thinking_progress(self, phase: str, progress: float, insight: str = "", 
                               confidence: float = 0.7) -> Dict[str, Any]:
        """Update avatar with thinking progress and insights"""
        
        # Map thinking phases to avatar states
        phase_states = {
            "analyzing": AvatarState.ANALYZING,
            "reasoning": AvatarState.REASONING,
            "calculating": AvatarState.CALCULATING,
            "synthesizing": AvatarState.SYNTHESIZING,
            "concluding": AvatarState.CONCLUDING,
        }
        
        # Update state if phase is recognized
        if phase in phase_states:
            self.update_state(phase_states[phase])
        
        # Adjust emotion based on confidence and phase
        if confidence > 0.8:
            emotion = AvatarEmotion.EXCITED if phase == "concluding" else AvatarEmotion.HAPPY
        elif confidence > 0.6:
            emotion = AvatarEmotion.THINKING
        else:
            emotion = AvatarEmotion.SURPRISED  # Uncertainty
        
        self.update_emotion("", "", force_emotion=emotion)
        
        return {
            "phase": phase,
            "progress": progress,
            "insight": insight,
            "confidence": confidence,
            "avatar_state": self.current_state.value,
            "avatar_emotion": self.current_emotion.value,
            "timestamp": time.time()
        }
    
    def update_reasoning_insight(self, reasoning_type: str, complexity: float, 
                               key_concepts: List[str] = None) -> Dict[str, Any]:
        """Update avatar based on reasoning insights"""
        
        # Adjust emotion based on reasoning type and complexity
        reasoning_emotions = {
            "mathematical": AvatarEmotion.THINKING,
            "logical": AvatarEmotion.THINKING,
            "creative": AvatarEmotion.EXCITED,
            "analytical": AvatarEmotion.THINKING,
            "conversational": AvatarEmotion.HAPPY,
        }
        
        base_emotion = reasoning_emotions.get(reasoning_type, AvatarEmotion.THINKING)
        
        # Intensity based on complexity (higher complexity = higher intensity)
        intensity = int(50 + (complexity * 50))  # 50-100 range
        
        self.update_emotion("", "", force_emotion=base_emotion)
        
        return {
            "reasoning_type": reasoning_type,
            "complexity": complexity,
            "key_concepts": key_concepts or [],
            "avatar_emotion": self.current_emotion.value,
            "emotion_intensity": intensity,
            "timestamp": time.time()
        }
    
    def get_current_state(self) -> Dict[str, Any]:
        """Get current avatar state"""
        return {
            "emotion": self.current_emotion.value,
            "state": self.current_state.value,
            "style": self.current_style.value,
            "color": self.current_color,
            "intensity": self.emotion_intensity,
            "last_update": self.last_update
        }
    
    def reset_to_idle(self):
        """Reset avatar to idle state"""
        self.current_state = AvatarState.IDLE
        self.current_emotion = AvatarEmotion.SLEEPY
        self.emotion_intensity = 30
        self.last_update = time.time()
    
    def get_emotion_for_ai_state(self, ai_state: str) -> AvatarEmotion:
        """Map AI states to avatar emotions"""
        state_mapping = {
            'loading': AvatarEmotion.THINKING,
            'thinking': AvatarEmotion.THINKING,
            'generating': AvatarEmotion.THINKING,
            'speaking': AvatarEmotion.HAPPY,
            'error': AvatarEmotion.SAD,
            'idle': AvatarEmotion.SLEEPY,
            'success': AvatarEmotion.EXCITED,
            'greeting': AvatarEmotion.HAPPY,
            'helping': AvatarEmotion.HAPPY
        }
        
        return state_mapping.get(ai_state.lower(), AvatarEmotion.HAPPY)
    
    def update_from_classification(self, classification_data: Dict[str, Any], text: str = "") -> Dict[str, Any]:
        """Update avatar based on enhanced AI classification results"""
        if not ENHANCED_CLASSIFICATION_AVAILABLE:
            # Fallback to text-based emotion analysis
            return self.update_emotion(text, "classification")
        
        # Extract classification data
        intent = classification_data.get("intent", "unknown")
        confidence = classification_data.get("confidence", 0.5)
        response_strategy = classification_data.get("response_strategy", "balanced")
        reasoning = classification_data.get("reasoning", "")
        context_factors = classification_data.get("context_factors", {})
        
        # Map intent to avatar emotion and state
        emotion, state = self._map_intent_to_avatar(intent, response_strategy, confidence)
        
        # Calculate intensity based on confidence and context
        intensity = self._calculate_classification_intensity(confidence, context_factors, response_strategy)
        
        # Update avatar state and emotion
        previous_emotion = self.current_emotion
        self.current_emotion = emotion
        self.emotion_intensity = intensity
        self.last_update = time.time()
        
        # Update state based on intent
        if intent in ['mathematical_calculation', 'code_debugging']:
            self.current_state = AvatarState.ANALYZING
        elif intent == 'creative_writing':
            self.current_state = AvatarState.SYNTHESIZING
        elif intent in ['explanation_request', 'instruction_request']:
            self.current_state = AvatarState.THINKING
        else:
            self.current_state = AvatarState.REASONING
        
        return {
            "emotion": emotion.value,
            "state": self.current_state.value,
            "intensity": intensity,
            "previous_emotion": previous_emotion.value,
            "changed": emotion != previous_emotion,
            "intent": intent,
            "confidence": confidence,
            "response_strategy": response_strategy,
            "reasoning": reasoning,
            "context_factors": context_factors,
            "timestamp": self.last_update
        }
    
    def _map_intent_to_avatar(self, intent: str, strategy: str, confidence: float) -> tuple:
        """Map AI intent and strategy to avatar emotion and behavior"""
        
        # Intent-based emotion mapping with enhanced sophistication
        intent_emotions = {
            'mathematical_calculation': AvatarEmotion.THINKING if confidence > 0.7 else AvatarEmotion.SURPRISED,
            'code_debugging': AvatarEmotion.THINKING,
            'factual_query': AvatarEmotion.HAPPY if confidence > 0.8 else AvatarEmotion.THINKING,
            'explanation_request': AvatarEmotion.EXCITED if confidence > 0.7 else AvatarEmotion.THINKING,
            'creative_writing': AvatarEmotion.EXCITED,
            'brainstorming': AvatarEmotion.EXCITED,
            'casual_conversation': AvatarEmotion.HAPPY,
            'greeting': AvatarEmotion.HAPPY,
            'help_request': AvatarEmotion.HAPPY,
            'instruction_request': AvatarEmotion.THINKING
        }
        
        base_emotion = intent_emotions.get(intent, AvatarEmotion.HAPPY)
        
        # Strategy-based modifications
        if strategy == 'creative':
            if base_emotion == AvatarEmotion.THINKING:
                base_emotion = AvatarEmotion.EXCITED
        elif strategy == 'deterministic':
            if base_emotion == AvatarEmotion.EXCITED:
                base_emotion = AvatarEmotion.THINKING
        
        # Confidence-based adjustments
        if confidence < 0.5:
            base_emotion = AvatarEmotion.SURPRISED
        elif confidence > 0.9:
            if base_emotion == AvatarEmotion.THINKING:
                base_emotion = AvatarEmotion.HAPPY
        
        # Determine state based on intent
        intent_states = {
            'mathematical_calculation': AvatarState.CALCULATING,
            'code_debugging': AvatarState.ANALYZING,
            'creative_writing': AvatarState.SYNTHESIZING,
            'explanation_request': AvatarState.REASONING,
            'factual_query': AvatarState.THINKING,
            'greeting': AvatarState.IDLE,
            'casual_conversation': AvatarState.IDLE
        }
        
        state = intent_states.get(intent, AvatarState.THINKING)
        
        return base_emotion, state
    
    def _calculate_classification_intensity(self, confidence: float, context_factors: Dict[str, Any], strategy: str) -> int:
        """Calculate avatar emotion intensity based on classification data"""
        
        # Base intensity from confidence (30-90 range)
        base_intensity = 30 + (confidence * 60)
        
        # Context-based adjustments
        if context_factors.get('urgent', False):
            base_intensity += 15
        
        if context_factors.get('learning_mode', False):
            base_intensity += 10
            
        if context_factors.get('technical_context', False):
            base_intensity += 5
        
        # Strategy-based adjustments
        strategy_modifiers = {
            'creative': 1.2,
            'deterministic': 0.9,
            'brief': 0.8,
            'detailed': 1.1,
            'conversational': 1.0
        }
        
        multiplier = strategy_modifiers.get(strategy, 1.0)
        final_intensity = base_intensity * multiplier
        
        return min(max(int(final_intensity), 10), 100)
    
    def update_from_enhanced_metadata(self, metadata: Dict[str, Any], text: str = "") -> Dict[str, Any]:
        """Update avatar from enhanced adaptive config metadata"""
        
        # Use classification data if available
        if 'intent' in metadata:
            return self.update_from_classification(metadata, text)
        
        # Fallback to traditional update
        return self.update_emotion(text, "enhanced_metadata")

# Utility functions for easy integration
def create_avatar_controller() -> AvatarController:
    """Create a new avatar controller instance"""
    return AvatarController()

def analyze_ai_response_emotion(text: str, context: str = "") -> Dict[str, Any]:
    """Quick function to analyze emotion from AI response"""
    controller = AvatarController()
    return controller.update_emotion(text, context)

if __name__ == "__main__":
    # Test the controller
    controller = AvatarController()
    
    # Test various texts
    test_cases = [
        ("Hello! How can I help you today?", "greeting"),
        ("I'm thinking about your question...", "processing"),
        ("That's a great idea! I love it!", ""),
        ("I'm sorry, but I can't help with that.", "error"),
        ("Hmm, let me think about this carefully.", ""),
        ("Wow! That's absolutely amazing!", ""),
        ("I'm feeling a bit tired right now.", "")
    ]
    
    print("🧪 Testing Avatar Controller")
    print("=" * 50)
    
    for text, context in test_cases:
        result = controller.update_emotion(text, context)
        print(f"Text: '{text}'")
        print(f"Context: '{context}'")
        print(f"Emotion: {result['emotion']} (intensity: {result['intensity']})")
        print(f"Changed: {result['changed']}")
        print("-" * 30)