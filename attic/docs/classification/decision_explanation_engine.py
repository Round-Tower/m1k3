#!/usr/bin/env python3
"""
Decision Explanation Engine
Provides transparent explanations of classification and configuration decisions
"""

import json
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass, asdict
from datetime import datetime

from intent_classification_system import IntentClassificationEngine, UserIntent, ResponseStrategy
from enhanced_adaptive_model_config import EnhancedAdaptiveModelConfig

@dataclass
class PatternMatch:
    """Information about a matched pattern"""
    pattern: str
    matched_text: str
    intent: str
    score: float
    explanation: str

@dataclass 
class ContextInfluence:
    """Information about how context influenced the decision"""
    factor: str
    value: Any
    influence: str
    impact_level: str  # 'low', 'medium', 'high'
    
@dataclass
class ParameterAdjustment:
    """Information about parameter adjustments"""
    parameter: str
    original_value: Any
    adjusted_value: Any
    reason: str
    source: str  # 'strategy', 'context', 'model', 'urgency', etc.

@dataclass
class DecisionExplanation:
    """Comprehensive explanation of a classification/configuration decision"""
    query: str
    timestamp: str
    
    # Intent classification
    final_intent: str
    intent_confidence: float
    pattern_matches: List[PatternMatch]
    
    # Response strategy 
    response_strategy: str
    strategy_reasoning: str
    
    # Context influences
    context_influences: List[ContextInfluence]
    
    # Parameter decisions
    parameter_adjustments: List[ParameterAdjustment]
    
    # Final configuration
    final_config: Dict[str, Any]
    
    # Summary
    decision_summary: str
    confidence_factors: List[str]
    uncertainty_factors: List[str]
    
    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
    
    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2, default=str)

class DecisionExplainerEngine:
    """Engine that provides transparent explanations of classification decisions"""
    
    def __init__(self):
        self.intent_engine = IntentClassificationEngine()
        
        # Pattern explanations
        self.pattern_explanations = self._initialize_pattern_explanations()
        
        # Strategy explanations
        self.strategy_explanations = self._initialize_strategy_explanations()
        
        # Parameter explanations
        self.parameter_explanations = self._initialize_parameter_explanations()
        
    def _initialize_pattern_explanations(self) -> Dict[str, str]:
        """Initialize explanations for regex patterns"""
        return {
            r'what\s+is\s+\d+\s*[+\-*/÷×]\s*\d+': "Detected mathematical question format (e.g., 'what is 5 + 3')",
            r'calculate\s+\d+\s*[+\-*/÷×]\s*\d+': "Detected calculation request with arithmetic expression",
            r'\d+\s*[+\-*/÷×]\s*\d+': "Found arithmetic expression with numbers and operators",
            r'\b(error|bug|debug|fix|broken|not working)\b': "Detected debugging/problem-solving keywords",
            r'^(how|why)\s': "Question starts with 'how' or 'why', indicating explanation request",
            r'\b(explain|describe|clarify)\b': "Contains explanation-seeking verbs",
            r'^(hi|hello|hey|greetings)': "Greeting detected at start of message",
            r'\b(write|story|poem|creative)\b': "Creative writing indicators found",
            r'^(help|assist|support)\b': "Direct help request detected"
        }
    
    def _initialize_strategy_explanations(self) -> Dict[ResponseStrategy, str]:
        """Initialize explanations for response strategies"""
        return {
            ResponseStrategy.DETERMINISTIC: "Precise, factual responses with no randomness - best for math and factual queries",
            ResponseStrategy.STRUCTURED: "Organized, step-by-step responses - ideal for tutorials and debugging",
            ResponseStrategy.CREATIVE: "Imaginative, free-flowing responses - perfect for creative tasks", 
            ResponseStrategy.CONVERSATIONAL: "Natural, engaging dialogue - suited for casual conversation",
            ResponseStrategy.BRIEF: "Concise, to-the-point responses - appropriate for greetings and quick questions",
            ResponseStrategy.DETAILED: "Comprehensive, thorough explanations - best for learning and complex topics",
            ResponseStrategy.BALANCED: "Mix of accuracy and creativity - versatile for most situations"
        }
        
    def _initialize_parameter_explanations(self) -> Dict[str, str]:
        """Initialize explanations for parameter choices"""
        return {
            'do_sample': "Controls randomness: False = deterministic, True = creative sampling",
            'temperature': "Controls creativity: lower = more focused, higher = more creative",
            'top_p': "Nucleus sampling: lower = more focused vocabulary, higher = more diverse",
            'top_k': "Top-k sampling: limits vocabulary to k most likely tokens",
            'max_new_tokens': "Maximum response length in tokens",
            'repetition_penalty': "Prevents repetitive text: higher values reduce repetition",
            'no_repeat_ngram_size': "Prevents repeating n-gram sequences",
            'num_beams': "Beam search: higher values = more thorough search, slower generation"
        }
    
    def explain_decision(self, query: str, config_result: Dict[str, Any], enhanced_config: EnhancedAdaptiveModelConfig = None) -> DecisionExplanation:
        """Generate comprehensive explanation of classification and configuration decision"""
        
        metadata = config_result.get('_metadata', {})
        
        # Re-run classification to get detailed pattern matches
        pattern_matches = self._analyze_pattern_matches(query, metadata.get('intent', 'unknown'))
        
        # Analyze context influences
        context_influences = self._analyze_context_influences(metadata.get('context_factors', {}))
        
        # Analyze parameter adjustments
        parameter_adjustments = self._analyze_parameter_adjustments(config_result, metadata)
        
        # Generate confidence and uncertainty factors
        confidence_factors, uncertainty_factors = self._analyze_confidence_factors(
            metadata.get('confidence', 0), pattern_matches, context_influences
        )
        
        # Create comprehensive summary
        decision_summary = self._generate_decision_summary(
            metadata.get('intent', 'unknown'),
            metadata.get('response_strategy', 'unknown'),
            pattern_matches,
            context_influences,
            parameter_adjustments
        )
        
        return DecisionExplanation(
            query=query,
            timestamp=datetime.now().isoformat(),
            
            final_intent=metadata.get('intent', 'unknown'),
            intent_confidence=metadata.get('confidence', 0),
            pattern_matches=pattern_matches,
            
            response_strategy=metadata.get('response_strategy', 'unknown'),
            strategy_reasoning=self._get_strategy_reasoning(
                metadata.get('intent', 'unknown'), 
                metadata.get('response_strategy', 'unknown'),
                context_influences
            ),
            
            context_influences=context_influences,
            parameter_adjustments=parameter_adjustments,
            
            final_config={k: v for k, v in config_result.items() if k != '_metadata' and v is not None},
            
            decision_summary=decision_summary,
            confidence_factors=confidence_factors,
            uncertainty_factors=uncertainty_factors
        )
    
    def _analyze_pattern_matches(self, query: str, final_intent: str) -> List[PatternMatch]:
        """Analyze which patterns matched and contributed to the classification"""
        matches = []
        
        # Check all intent patterns
        for intent, pattern_obj in self.intent_engine.intent_patterns.items():
            for i, regex_pattern in enumerate(pattern_obj.regex_patterns):
                match = regex_pattern.search(query)
                if match:
                    pattern_str = regex_pattern.pattern
                    explanation = self.pattern_explanations.get(pattern_str, f"Pattern #{i+1} for {intent.value}")
                    
                    # Calculate relative score contribution
                    score = 1.0 / len(pattern_obj.regex_patterns)
                    
                    matches.append(PatternMatch(
                        pattern=pattern_str,
                        matched_text=match.group(0),
                        intent=intent.value,
                        score=score,
                        explanation=explanation
                    ))
        
        # Sort by score (highest first) and whether they match the final intent
        matches.sort(key=lambda x: (x.intent == final_intent, x.score), reverse=True)
        return matches
    
    def _analyze_context_influences(self, context_factors: Dict[str, Any]) -> List[ContextInfluence]:
        """Analyze how context factors influenced the decision"""
        influences = []
        
        context_explanations = {
            'urgent': ("High urgency detected", "Shortened response and faster generation", "high"),
            'learning_mode': ("Learning context identified", "More detailed explanations provided", "medium"),
            'casual_mode': ("Casual conversation detected", "More relaxed and natural tone", "low"),
            'technical_context': ("Technical topic identified", "More structured and precise responses", "medium"),
            'user_mood': ("User emotional state detected", "Response style adapted to mood", "medium"),
            'fast_typing': ("Fast typing detected", "Indicates user urgency", "medium"),
            'query_complexity': ("Query complexity analyzed", "Response depth adjusted accordingly", "low"),
            'websocket_data': ("Real-time context available", "Live user state incorporated", "high")
        }
        
        for factor, value in context_factors.items():
            if factor in context_explanations and value not in [None, False, '', {}]:
                explanation, influence, impact = context_explanations[factor]
                influences.append(ContextInfluence(
                    factor=factor,
                    value=value,
                    influence=influence,
                    impact_level=impact
                ))
        
        return influences
    
    def _analyze_parameter_adjustments(self, config: Dict[str, Any], metadata: Dict[str, Any]) -> List[ParameterAdjustment]:
        """Analyze parameter adjustments and their reasons"""
        adjustments = []
        
        # Get base strategy configuration
        strategy = metadata.get('response_strategy', 'balanced')
        intent = metadata.get('intent', 'unknown')
        context_factors = metadata.get('context_factors', {})
        
        # Analyze key parameters
        parameter_analysis = {
            'do_sample': self._analyze_sampling_decision(config, intent, context_factors),
            'temperature': self._analyze_temperature_decision(config, strategy, context_factors),
            'max_new_tokens': self._analyze_length_decision(config, strategy, context_factors),
            'repetition_penalty': self._analyze_repetition_decision(config, intent),
            'num_beams': self._analyze_beam_decision(config, strategy)
        }
        
        for param, analysis in parameter_analysis.items():
            if analysis:
                adjustments.append(ParameterAdjustment(
                    parameter=param,
                    original_value=analysis.get('original'),
                    adjusted_value=config.get(param),
                    reason=analysis.get('reason', 'Strategy-based setting'),
                    source=analysis.get('source', 'strategy')
                ))
        
        return adjustments
    
    def _analyze_sampling_decision(self, config: Dict[str, Any], intent: str, context: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Analyze the do_sample parameter decision"""
        do_sample = config.get('do_sample')
        
        if intent in ['mathematical_calculation', 'factual_query']:
            return {
                'original': True,
                'reason': f"Deterministic generation chosen for {intent} to ensure accuracy",
                'source': 'intent'
            }
        elif context.get('urgent'):
            return {
                'original': True,
                'reason': "Deterministic generation for faster, more direct responses due to urgency",
                'source': 'context'
            }
        elif do_sample is True:
            return {
                'original': False,
                'reason': "Sampling enabled for more natural and varied responses",
                'source': 'strategy'
            }
        
        return None
    
    def _analyze_temperature_decision(self, config: Dict[str, Any], strategy: str, context: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Analyze temperature parameter decision"""
        temp = config.get('temperature')
        
        if temp is None:
            return {
                'original': 0.7,
                'reason': "Temperature disabled due to deterministic generation (do_sample=False)",
                'source': 'sampling_mode'
            }
        elif temp > 0.8:
            return {
                'original': 0.7,
                'reason': f"High temperature ({temp}) for creative and diverse responses",
                'source': 'strategy'
            }
        elif temp < 0.7:
            return {
                'original': 0.7,
                'reason': f"Lower temperature ({temp}) for more focused and consistent responses",
                'source': 'strategy'
            }
        
        return None
    
    def _analyze_length_decision(self, config: Dict[str, Any], strategy: str, context: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Analyze max_new_tokens decision"""
        tokens = config.get('max_new_tokens', 200)
        
        if context.get('urgent') and tokens < 200:
            return {
                'original': 400,
                'reason': f"Reduced to {tokens} tokens due to urgency context",
                'source': 'context'
            }
        elif context.get('learning_mode') and tokens > 300:
            return {
                'original': 200,
                'reason': f"Increased to {tokens} tokens for detailed educational content",
                'source': 'context'
            }
        elif strategy == 'brief' and tokens <= 100:
            return {
                'original': 200,
                'reason': f"Limited to {tokens} tokens for brief response strategy",
                'source': 'strategy'
            }
        elif strategy == 'detailed' and tokens >= 400:
            return {
                'original': 200,
                'reason': f"Increased to {tokens} tokens for comprehensive explanations",
                'source': 'strategy'
            }
        
        return None
    
    def _analyze_repetition_decision(self, config: Dict[str, Any], intent: str) -> Optional[Dict[str, Any]]:
        """Analyze repetition_penalty decision"""
        penalty = config.get('repetition_penalty', 1.0)
        
        if intent == 'mathematical_calculation' and penalty > 1.05:
            return {
                'original': 1.0,
                'reason': f"Higher repetition penalty ({penalty}) to prevent mathematical expression loops",
                'source': 'intent'
            }
        
        return None
    
    def _analyze_beam_decision(self, config: Dict[str, Any], strategy: str) -> Optional[Dict[str, Any]]:
        """Analyze num_beams decision"""
        beams = config.get('num_beams', 1)
        
        if strategy == 'structured' and beams > 1:
            return {
                'original': 1,
                'reason': f"Beam search ({beams} beams) for more coherent structured responses",
                'source': 'strategy'
            }
        
        return None
    
    def _get_strategy_reasoning(self, intent: str, strategy: str, context_influences: List[ContextInfluence]) -> str:
        """Generate reasoning for strategy selection"""
        base_reasoning = f"Intent '{intent}' typically maps to '{strategy}' strategy"
        
        if self.strategy_explanations.get(ResponseStrategy(strategy)):
            base_reasoning += f": {self.strategy_explanations[ResponseStrategy(strategy)]}"
        
        # Add context modifications
        high_impact_influences = [inf for inf in context_influences if inf.impact_level == 'high']
        if high_impact_influences:
            influences_text = ", ".join([inf.influence for inf in high_impact_influences])
            base_reasoning += f". Modified by context: {influences_text}"
        
        return base_reasoning
    
    def _analyze_confidence_factors(self, confidence: float, pattern_matches: List[PatternMatch], context_influences: List[ContextInfluence]) -> Tuple[List[str], List[str]]:
        """Analyze factors that increase/decrease confidence"""
        confidence_factors = []
        uncertainty_factors = []
        
        # Pattern match analysis
        strong_matches = [m for m in pattern_matches if m.score >= 0.5]
        if len(strong_matches) > 1:
            confidence_factors.append(f"Multiple strong pattern matches ({len(strong_matches)} patterns)")
        elif len(strong_matches) == 1:
            confidence_factors.append("Single strong pattern match")
        else:
            uncertainty_factors.append("No strong pattern matches found")
        
        # Context analysis
        high_impact_context = [c for c in context_influences if c.impact_level == 'high']
        if high_impact_context:
            confidence_factors.append(f"High-impact context available ({len(high_impact_context)} factors)")
        
        # Confidence level analysis
        if confidence > 0.8:
            confidence_factors.append("High confidence score indicates clear classification")
        elif confidence < 0.5:
            uncertainty_factors.append("Low confidence score suggests ambiguous query")
        
        return confidence_factors, uncertainty_factors
    
    def _generate_decision_summary(self, intent: str, strategy: str, pattern_matches: List[PatternMatch], 
                                 context_influences: List[ContextInfluence], parameter_adjustments: List[ParameterAdjustment]) -> str:
        """Generate a human-readable summary of the decision process"""
        
        # Start with intent classification
        summary = f"Classified as '{intent}' based on "
        
        if pattern_matches:
            primary_matches = [m for m in pattern_matches if m.intent == intent][:2]
            if primary_matches:
                match_descriptions = [f"'{m.matched_text}'" for m in primary_matches]
                summary += f"patterns: {', '.join(match_descriptions)}. "
        
        # Strategy selection
        summary += f"Selected '{strategy}' response strategy. "
        
        # Context influences
        high_impact = [c for c in context_influences if c.impact_level == 'high']
        if high_impact:
            influences = [c.influence.lower() for c in high_impact]
            summary += f"Context adjustments: {', '.join(influences)}. "
        
        # Key parameter adjustments
        key_adjustments = [adj for adj in parameter_adjustments if adj.source in ['context', 'urgency']]
        if key_adjustments:
            adj_descriptions = [f"{adj.parameter} adjusted for {adj.reason.lower()}" for adj in key_adjustments[:2]]
            summary += f"Parameters: {', '.join(adj_descriptions)}."
        
        return summary

# Testing functionality
def test_decision_explanation():
    """Test the decision explanation engine"""
    print("🔍 Testing Decision Explanation Engine")
    print("=" * 60)
    
    explainer = DecisionExplainerEngine()
    enhanced_config = EnhancedAdaptiveModelConfig(enable_websocket=True)
    
    # Add some context
    from websocket_context_server import ContextData
    import time
    
    enhanced_config.add_context_data(ContextData(
        timestamp=time.time(),
        source='user_state',
        data_type='user_mood',
        value='focused',
        confidence=0.9
    ))
    
    enhanced_config.add_context_data(ContextData(
        timestamp=time.time(),
        source='project_tracker',
        data_type='task_urgency',
        value='high',
        confidence=0.95
    ))
    
    # Test query
    query = "What is 25 * 17 + 43?"
    config_result = enhanced_config.get_optimal_config(query, "qwen/qwen3-0.6b", [])
    
    # Generate explanation
    explanation = explainer.explain_decision(query, config_result, enhanced_config)
    
    print(f"📝 Query: \"{query}\"")
    print(f"\n🎯 Decision Summary:")
    print(f"   {explanation.decision_summary}")
    
    print(f"\n📊 Intent Classification:")
    print(f"   Intent: {explanation.final_intent} (confidence: {explanation.intent_confidence:.3f})")
    
    print(f"\n🔍 Pattern Matches:")
    for match in explanation.pattern_matches[:3]:  # Show top 3
        print(f"   ✅ '{match.matched_text}' → {match.intent} (score: {match.score:.3f})")
        print(f"      {match.explanation}")
    
    print(f"\n🎭 Response Strategy:")
    print(f"   Strategy: {explanation.response_strategy}")
    print(f"   Reasoning: {explanation.strategy_reasoning}")
    
    print(f"\n🌐 Context Influences:")
    for influence in explanation.context_influences:
        impact_icon = {"high": "🔥", "medium": "🟡", "low": "🟢"}[influence.impact_level]
        print(f"   {impact_icon} {influence.factor}: {influence.value}")
        print(f"      {influence.influence}")
    
    print(f"\n⚙️  Parameter Adjustments:")
    for adj in explanation.parameter_adjustments:
        print(f"   🔧 {adj.parameter}: {adj.original_value} → {adj.adjusted_value}")
        print(f"      Reason: {adj.reason} (source: {adj.source})")
    
    print(f"\n📈 Confidence Analysis:")
    if explanation.confidence_factors:
        print("   Confidence boosters:")
        for factor in explanation.confidence_factors:
            print(f"   ✅ {factor}")
    
    if explanation.uncertainty_factors:
        print("   Uncertainty factors:")
        for factor in explanation.uncertainty_factors:
            print(f"   ⚠️  {factor}")

if __name__ == "__main__":
    test_decision_explanation()