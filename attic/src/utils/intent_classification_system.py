#!/usr/bin/env python3
"""
Intent-based Classification System
Separates intent detection from generation strategy selection
"""

import re
import json
from typing import Dict, List, Tuple, Optional, Any
from enum import Enum
from dataclasses import dataclass
import time

class UserIntent(Enum):
    """User intent categories - what the user is trying to accomplish"""
    # Information seeking
    FACTUAL_QUERY = "factual_query"
    EXPLANATION_REQUEST = "explanation_request" 
    DEFINITION_REQUEST = "definition_request"
    COMPARISON_REQUEST = "comparison_request"
    
    # Problem solving
    MATHEMATICAL_CALCULATION = "mathematical_calculation"
    CODE_DEBUGGING = "code_debugging"
    TROUBLESHOOTING = "troubleshooting"
    ANALYSIS_REQUEST = "analysis_request"
    
    # Creative tasks
    CREATIVE_WRITING = "creative_writing"
    BRAINSTORMING = "brainstorming"
    DESIGN_REQUEST = "design_request"
    
    # Communication
    CASUAL_CONVERSATION = "casual_conversation"
    GREETING = "greeting"
    FAREWELL = "farewell"
    
    # Task execution
    INSTRUCTION_REQUEST = "instruction_request"
    PLANNING_REQUEST = "planning_request"
    RECOMMENDATION_REQUEST = "recommendation_request"
    
    # Meta/system
    SYSTEM_QUERY = "system_query"
    HELP_REQUEST = "help_request"
    UNCLEAR = "unclear"
    NEEDS_CLARIFICATION = "needs_clarification"

class ResponseStrategy(Enum):
    """Response generation strategies - how to respond"""
    DETERMINISTIC = "deterministic"      # Exact, factual, mathematical
    BALANCED = "balanced"                # Mix of creativity and accuracy
    CREATIVE = "creative"                # Open-ended, imaginative
    CONVERSATIONAL = "conversational"   # Natural, flowing dialogue
    STRUCTURED = "structured"           # Step-by-step, organized
    BRIEF = "brief"                      # Concise, to-the-point
    DETAILED = "detailed"                # Comprehensive, thorough

class ConfidenceLevel(Enum):
    """Confidence in classification"""
    HIGH = "high"      # > 0.8
    MEDIUM = "medium"  # 0.5 - 0.8
    LOW = "low"        # < 0.5

@dataclass
class IntentClassification:
    """Result of intent classification"""
    intent: UserIntent
    confidence: float
    response_strategy: ResponseStrategy
    context_factors: Dict[str, Any]
    reasoning: str
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'intent': self.intent.value,
            'confidence': self.confidence,
            'response_strategy': self.response_strategy.value,
            'context_factors': self.context_factors,
            'reasoning': self.reasoning
        }

class IntentPattern:
    """Pattern for detecting user intent"""
    
    def __init__(self, intent: UserIntent, patterns: List[str], weights: Dict[str, float] = None):
        self.intent = intent
        self.regex_patterns = [re.compile(p, re.IGNORECASE) for p in patterns]
        self.weights = weights or {}
    
    def match_score(self, query: str, context: Dict[str, Any] = None) -> float:
        """Calculate match score for this intent"""
        matches = 0
        
        # Check regex patterns
        for pattern in self.regex_patterns:
            if pattern.search(query):
                matches += 1
        
        if matches == 0:
            return 0.0
        
        # Base score: more matches = higher confidence
        # Use square root to prevent too much penalty for many patterns
        base_score = min(matches / max(len(self.regex_patterns) ** 0.5, 1), 1.0)
        
        # Boost for multiple matches of the same intent
        if matches > 1:
            base_score = min(base_score * (1 + 0.2 * (matches - 1)), 1.0)
        
        # Apply context weights
        if context:
            for factor, weight in self.weights.items():
                if factor in context:
                    base_score += weight * context[factor]
        
        return min(base_score, 1.0)

class IntentClassificationEngine:
    """Main intent classification engine"""
    
    def __init__(self):
        self.intent_patterns = self._initialize_patterns()
        self.strategy_mappings = self._initialize_strategy_mappings()
        self.context_enrichers = []
        
    def _initialize_patterns(self) -> Dict[UserIntent, IntentPattern]:
        """Initialize intent detection patterns"""
        patterns = {
            # Mathematical calculations  
            UserIntent.MATHEMATICAL_CALCULATION: IntentPattern(
                UserIntent.MATHEMATICAL_CALCULATION,
                [
                    r'what\s+is\s+\d+\s*[+\-*/÷×]\s*\d+',   # "what is 15 * 23"
                    r'calculate\s+\d+\s*[+\-*/÷×]\s*\d+',   # "calculate 5 + 3"
                    r'^\d+\s*[+\-*/÷×]\s*\d+\s*[=?]?$',     # "15 * 23" or "15 * 23?"
                    r'\d+\s*[+\-*/÷×]\s*\d+',               # Any arithmetic expression
                    r'\b(sum|product|quotient|difference|calculate|compute|multiply|divide|add|subtract)\b',
                    r'percentage|percent|%',
                    r'\b(square|cube|root|power|equals)\b',
                    r'what.*(plus|minus|times|divided by)',  # "what is 5 plus 3"
                    r'\b\d+\s*\*\s*\d+\b'                   # Explicit multiplication
                ],
                {'recent_math_queries': 0.2, 'calculator_context': 0.3}
            ),
            
            # Code debugging
            UserIntent.CODE_DEBUGGING: IntentPattern(
                UserIntent.CODE_DEBUGGING,
                [
                    r'\b(error|bug|debug|fix|broken|not working)\b',
                    r'\b(exception|traceback|stack trace)\b',
                    r'why (is|does|doesn\'t).*(work|working)',
                    r'\b(syntax error|runtime error|logic error)\b',
                    r'how to fix',
                    r'\b(undefined|null pointer|segmentation fault)\b'
                ],
                {'coding_context': 0.4, 'error_keywords': 0.3}
            ),
            
            # Factual queries
            UserIntent.FACTUAL_QUERY: IntentPattern(
                UserIntent.FACTUAL_QUERY,
                [
                    r'^(what|when|where|who|which)\s',
                    r'\b(tell me about|information about)\b',
                    r'(is|are)\s+.+\?$',
                    r'\b(fact|facts|true|false)\b',
                    r'(how many|how much|how long)',
                    r'\b(statistics|data|numbers)\b'
                ],
                {'encyclopedia_context': 0.2, 'research_mode': 0.3}
            ),
            
            # Explanations
            UserIntent.EXPLANATION_REQUEST: IntentPattern(
                UserIntent.EXPLANATION_REQUEST,
                [
                    r'^(how|why)\s',
                    r'\b(explain|describe|clarify)\b',
                    r'can you (explain|tell me how|show me how)',
                    r'\b(understand|comprehend|grasp)\b',
                    r'(what does .+ mean|meaning of)',
                    r'\b(process|procedure|mechanism)\b'
                ],
                {'learning_context': 0.3, 'educational_mode': 0.2}
            ),
            
            # Greetings
            UserIntent.GREETING: IntentPattern(
                UserIntent.GREETING,
                [
                    r'^(hi|hello|hey|greetings|good morning|good afternoon|good evening)($|\s|\W)',
                    r'\b(how are you|how\'s it going)\b',
                    r'^(sup|yo|what\'s up)($|\s|\W)'
                ],
                {'conversation_start': 0.5, 'casual_mode': 0.2}
            ),
            
            # Creative writing
            UserIntent.CREATIVE_WRITING: IntentPattern(
                UserIntent.CREATIVE_WRITING,
                [
                    r'\b(write|story|poem|creative|imagine)\b',
                    r'(create|generate).+(story|text|content)',
                    r'\b(fiction|narrative|character|plot)\b',
                    r'(once upon a time|in a world where)',
                    r'\b(brainstorm|ideas|creative)\b'
                ],
                {'creative_mode': 0.4, 'artistic_context': 0.3}
            ),
            
            # Instructions/how-to
            UserIntent.INSTRUCTION_REQUEST: IntentPattern(
                UserIntent.INSTRUCTION_REQUEST,
                [
                    r'^(how to|how do I|how can I)\s',
                    r'\b(steps|instructions|tutorial|guide)\b',
                    r'(teach me|show me how)',
                    r'\b(procedure|process|method)\b',
                    r'(walk me through|step by step)',
                    r'\b(recipe|directions)\b'
                ],
                {'tutorial_mode': 0.3, 'learning_context': 0.2}
            ),
            
            # System/help queries
            UserIntent.HELP_REQUEST: IntentPattern(
                UserIntent.HELP_REQUEST,
                [
                    r'^(help|assist|support)\b',
                    r'\b(commands|options|features)\b',
                    r'(what can you|how do you work)',
                    r'\b(documentation|manual|guide)\b',
                    r'(getting started|how to use)'
                ],
                {'system_context': 0.4, 'documentation_mode': 0.3}
            ),
            
            # Casual conversation
            UserIntent.CASUAL_CONVERSATION: IntentPattern(
                UserIntent.CASUAL_CONVERSATION,
                [
                    r'\b(think|feel|opinion|believe)\b',
                    r'(just chatting|random|whatever)',
                    r'\b(interesting|cool|awesome|neat)\b',
                    r'(by the way|anyway|oh)',
                    r'\b(weather|weekend|today)\b'
                ],
                {'casual_mode': 0.3, 'social_context': 0.2}
            ),
            
            # Ambiguous or unclear requests needing clarification
            UserIntent.NEEDS_CLARIFICATION: IntentPattern(
                UserIntent.NEEDS_CLARIFICATION,
                [
                    r'^(that|this|it|them|those)($|\s)',  # Vague pronouns without context
                    r'\b(something|anything|whatever)\b',   # Vague descriptors
                    r'^(fix|do|make|change|update)($|\s)',  # Actions without objects
                    r'\b(the (thing|stuff|error|issue|problem))(?!\s+(is|was|with))', # Vague references
                    r'^(help|please|can you)($|\s)',       # Very general requests
                    r'^\w{1,3}$',                          # Very short queries
                    r'\b(i|you|we)\s+(need|want|should)\s+(to|the)?\s*$', # Incomplete thoughts
                    r'\?{2,}',                             # Multiple question marks indicating confusion
                    r'\b(uh|um|er|ah)\b'                   # Hesitation words
                ],
                {'low_confidence': 0.4, 'missing_context': 0.5, 'ambiguous_pronouns': 0.3}
            )
        }
        return patterns
    
    def _initialize_strategy_mappings(self) -> Dict[UserIntent, ResponseStrategy]:
        """Map intents to preferred response strategies"""
        return {
            UserIntent.MATHEMATICAL_CALCULATION: ResponseStrategy.DETERMINISTIC,
            UserIntent.CODE_DEBUGGING: ResponseStrategy.STRUCTURED,
            UserIntent.FACTUAL_QUERY: ResponseStrategy.DETERMINISTIC,
            UserIntent.EXPLANATION_REQUEST: ResponseStrategy.DETAILED,
            UserIntent.DEFINITION_REQUEST: ResponseStrategy.STRUCTURED,
            UserIntent.COMPARISON_REQUEST: ResponseStrategy.BALANCED,
            UserIntent.TROUBLESHOOTING: ResponseStrategy.STRUCTURED,
            UserIntent.ANALYSIS_REQUEST: ResponseStrategy.DETAILED,
            UserIntent.CREATIVE_WRITING: ResponseStrategy.CREATIVE,
            UserIntent.BRAINSTORMING: ResponseStrategy.CREATIVE,
            UserIntent.DESIGN_REQUEST: ResponseStrategy.CREATIVE,
            UserIntent.CASUAL_CONVERSATION: ResponseStrategy.CONVERSATIONAL,
            UserIntent.GREETING: ResponseStrategy.BRIEF,
            UserIntent.FAREWELL: ResponseStrategy.BRIEF,
            UserIntent.INSTRUCTION_REQUEST: ResponseStrategy.STRUCTURED,
            UserIntent.PLANNING_REQUEST: ResponseStrategy.STRUCTURED,
            UserIntent.RECOMMENDATION_REQUEST: ResponseStrategy.BALANCED,
            UserIntent.SYSTEM_QUERY: ResponseStrategy.STRUCTURED,
            UserIntent.HELP_REQUEST: ResponseStrategy.DETAILED,
            UserIntent.UNCLEAR: ResponseStrategy.CONVERSATIONAL,
            UserIntent.NEEDS_CLARIFICATION: ResponseStrategy.CONVERSATIONAL
        }
    
    def add_context_enricher(self, enricher_func):
        """Add a function to enrich context data"""
        self.context_enrichers.append(enricher_func)
    
    def classify_intent(self, query: str, context: Dict[str, Any] = None) -> IntentClassification:
        """Classify user intent and determine response strategy"""
        if context is None:
            context = {}
            
        # Enrich context with additional data
        for enricher in self.context_enrichers:
            try:
                additional_context = enricher(query, context)
                context.update(additional_context)
            except Exception as e:
                # Don't let enricher errors break classification
                pass
        
        # Score all intents
        intent_scores = {}
        for intent, pattern in self.intent_patterns.items():
            score = pattern.match_score(query, context)
            if score > 0:
                intent_scores[intent] = score
        
        # Find best match with special handling for mathematical expressions
        if not intent_scores:
            best_intent = UserIntent.UNCLEAR
            confidence = 0.3
            reasoning = "No clear intent patterns matched"
        else:
            # Special case: if mathematical_calculation scored well and query contains arithmetic,
            # boost its confidence to prioritize over generic factual patterns
            if (UserIntent.MATHEMATICAL_CALCULATION in intent_scores and 
                intent_scores[UserIntent.MATHEMATICAL_CALCULATION] > 0.5 and
                any(op in query for op in ['+', '-', '*', '/', '×', '÷', '='])):
                
                # Boost mathematical confidence when arithmetic expressions are present
                intent_scores[UserIntent.MATHEMATICAL_CALCULATION] = min(
                    intent_scores[UserIntent.MATHEMATICAL_CALCULATION] * 1.5, 1.0
                )
            
            best_intent = max(intent_scores, key=intent_scores.get)
            confidence = intent_scores[best_intent]
            reasoning = f"Matched {len([s for s in intent_scores.values() if s > 0])} patterns, highest score: {confidence:.3f}"
        
        # Boost confidence if multiple patterns match the same intent
        if best_intent in intent_scores and len(intent_scores) > 1:
            confidence = min(confidence * 1.2, 1.0)
        
        # Check if clarification is needed (override other intents if confidence is very low)
        needs_clarification = self._detect_clarification_need(query, best_intent, confidence, intent_scores)
        if needs_clarification:
            best_intent = UserIntent.NEEDS_CLARIFICATION
            confidence = max(confidence, 0.6)  # Give reasonable confidence for clarification
            reasoning = f"Low confidence ({confidence:.3f}) or ambiguous query detected"
        
        # Get response strategy
        response_strategy = self.strategy_mappings.get(best_intent, ResponseStrategy.BALANCED)
        
        # Context-aware strategy adjustments
        if context.get('urgent', False) and response_strategy == ResponseStrategy.DETAILED:
            response_strategy = ResponseStrategy.BRIEF
        elif context.get('learning_mode', False):
            response_strategy = ResponseStrategy.DETAILED
        elif context.get('casual_mode', False):
            response_strategy = ResponseStrategy.CONVERSATIONAL
        
        return IntentClassification(
            intent=best_intent,
            confidence=confidence,
            response_strategy=response_strategy,
            context_factors=context,
            reasoning=reasoning
        )
    
    def _detect_clarification_need(self, query: str, best_intent: UserIntent, confidence: float, intent_scores: Dict) -> bool:
        """Detect if the query needs clarification"""
        
        # 1. Very low confidence threshold
        if confidence < 0.3:
            return True
            
        # 2. Multiple competing intents with similar scores
        if len(intent_scores) >= 2:
            sorted_scores = sorted(intent_scores.values(), reverse=True)
            if len(sorted_scores) >= 2 and sorted_scores[0] - sorted_scores[1] < 0.1:
                return True
        
        # 3. Check for explicit clarification patterns (handled by NEEDS_CLARIFICATION intent)
        if best_intent == UserIntent.NEEDS_CLARIFICATION:
            return True
            
        # 4. Very short queries that might be ambiguous
        if len(query.strip()) <= 2:
            return True
            
        # 5. Queries that start with pronouns without context
        pronouns_without_context = ['that', 'this', 'it', 'them', 'those']
        first_word = query.strip().split()[0].lower() if query.strip() else ""
        if first_word in pronouns_without_context:
            return True
            
        # 6. Generic action verbs without objects
        if query.strip().lower() in ['fix', 'do', 'make', 'change', 'update', 'help']:
            return True
            
        return False
    
    def get_confidence_level(self, confidence: float) -> ConfidenceLevel:
        """Convert confidence score to level"""
        if confidence > 0.8:
            return ConfidenceLevel.HIGH
        elif confidence > 0.5:
            return ConfidenceLevel.MEDIUM
        else:
            return ConfidenceLevel.LOW

# Context enricher functions
def basic_context_enricher(query: str, existing_context: Dict[str, Any]) -> Dict[str, Any]:
    """Basic context enrichment based on query analysis"""
    context = {}
    
    # Detect urgency indicators
    urgency_keywords = ['urgent', 'asap', 'quick', 'fast', 'immediate', 'deadline', 'emergency']
    if any(keyword in query.lower() for keyword in urgency_keywords):
        context['urgent'] = True
    
    # Detect learning mode
    learning_keywords = ['learn', 'teach', 'understand', 'explain', 'study', 'educational']
    if any(keyword in query.lower() for keyword in learning_keywords):
        context['learning_mode'] = True
    
    # Detect casual mode
    casual_keywords = ['just', 'casual', 'chat', 'random', 'whatever', 'btw', 'anyway']
    if any(keyword in query.lower() for keyword in casual_keywords):
        context['casual_mode'] = True
    
    # Detect technical context
    tech_keywords = ['code', 'program', 'debug', 'error', 'api', 'database', 'algorithm']
    if any(keyword in query.lower() for keyword in tech_keywords):
        context['technical_context'] = True
    
    # Query complexity (rough heuristic)
    word_count = len(query.split())
    context['query_complexity'] = 'high' if word_count > 20 else 'medium' if word_count > 10 else 'low'
    
    return context

# Testing functionality
def test_intent_classification():
    """Test the intent classification system"""
    print("🧠 Testing Intent Classification System")
    print("=" * 50)
    
    engine = IntentClassificationEngine()
    engine.add_context_enricher(basic_context_enricher)
    
    test_queries = [
        "What is 15 * 23?",
        "Hello, how are you?",
        "How do I fix this Python error?",
        "Explain how neural networks work",
        "Write a story about a robot",
        "What's the capital of France?",
        "Can you help me debug this code?",
        "Good morning!",
        "How to bake a cake?",
        "I need urgent help with this project deadline",
        "Just chatting, what do you think about AI?",
        "Tell me about the history of computers",
    ]
    
    for query in test_queries:
        classification = engine.classify_intent(query)
        confidence_level = engine.get_confidence_level(classification.confidence)
        
        print(f"\n📝 Query: \"{query}\"")
        print(f"🎯 Intent: {classification.intent.value}")
        print(f"📊 Confidence: {classification.confidence:.3f} ({confidence_level.value})")
        print(f"🎭 Strategy: {classification.response_strategy.value}")
        print(f"🔍 Reasoning: {classification.reasoning}")
        
        if classification.context_factors:
            print(f"📋 Context: {classification.context_factors}")

if __name__ == "__main__":
    test_intent_classification()