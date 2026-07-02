#!/usr/bin/env python3
"""
M1K3 Thinking Content Parser
Advanced parsing of model thinking processes for insight extraction
"""

import re
import math
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
from enum import Enum

class ReasoningType(Enum):
    MATHEMATICAL = "mathematical"
    LOGICAL = "logical"
    CREATIVE = "creative"
    ANALYTICAL = "analytical"
    CONVERSATIONAL = "conversational"

class ConfidenceLevel(Enum):
    VERY_HIGH = "very_high"    # 0.9+
    HIGH = "high"              # 0.7-0.9
    MEDIUM = "medium"          # 0.5-0.7
    LOW = "low"                # 0.3-0.5
    VERY_LOW = "very_low"      # <0.3

@dataclass
class ReasoningInsight:
    """Extracted insight from a piece of reasoning"""
    content: str
    reasoning_type: ReasoningType
    confidence: float
    emotion: str
    complexity_score: float
    contains_error: bool
    key_concepts: List[str]

@dataclass
class ThinkingFlow:
    """Analysis of the entire thinking flow"""
    insights: List[ReasoningInsight]
    final_answer: str
    overall_confidence: float
    reasoning_coherence: float
    emotional_journey: List[str]
    key_breakthroughs: List[str]
    potential_issues: List[str]
    final_confidence: ConfidenceLevel
    confidence_score: float  # Alias for overall_confidence for compatibility
    reasoning_quality: str   # Quality assessment string
    should_show_reasoning: bool
    emotion_progression: List[str]  # Alias for emotional_journey for compatibility

class ThinkingContentParser:
    """
    Advanced parser for extracting insights from model thinking processes
    """
    
    def __init__(self):
        # Mathematical reasoning patterns
        self.math_patterns = [
            r'\d+\s*[+\-*/]\s*\d+',
            r'equals?|=',
            r'calculate|computing|math|arithmetic',
            r'add|subtract|multiply|divide',
            r'sum|difference|product|quotient',
        ]
        
        # Logical reasoning patterns
        self.logic_patterns = [
            r'if\s+.*\s+then',
            r'because|since|therefore|thus|hence',
            r'given that|assuming|suppose',
            r'implies|means|suggests',
            r'conclusion|deduce|infer',
        ]
        
        # Creative thinking patterns
        self.creative_patterns = [
            r'imagine|creative|artistic|innovative',
            r'brainstorm|generate|come up with',
            r'unique|original|novel',
            r'metaphor|analogy|like',
            r'inspiration|creative',
        ]
        
        # Analytical thinking patterns
        self.analytical_patterns = [
            r'analyze|examine|investigate|study',
            r'break down|decompose|dissect',
            r'component|element|factor|aspect',
            r'pattern|trend|relationship',
            r'compare|contrast|evaluate',
        ]
        
        # Confidence indicators
        self.confidence_indicators = {
            'very_high': [r'definitely|certainly|absolutely|clearly|obviously|undoubtedly'],
            'high': [r'likely|probably|confident|sure|certain'],
            'medium': [r'seems|appears|suggests|indicates|probably'],
            'low': [r'maybe|perhaps|might|could|possibly|uncertain'],
            'very_low': [r'confused|unsure|unclear|don\'t know|not sure|doubt'],
        }
        
        # Emotional indicators in reasoning
        self.emotion_patterns = {
            'excited': [r'interesting|fascinating|amazing|great|excellent|wonderful'],
            'focused': [r'step by step|methodically|carefully|systematically|precisely'],
            'confused': [r'wait|hmm|confused|unclear|don\'t understand|mixed up'],
            'confident': [r'clearly|obviously|definitely|certain|sure'],
            'curious': [r'wonder|curious|interesting|explore|investigate'],
            'satisfied': [r'good|makes sense|perfect|correct|right'],
            'frustrated': [r'difficult|hard|challenging|struggling|stuck'],
            'thinking': [r'let me think|considering|pondering|reflecting|analyzing'],
        }
        
        # Error/problem indicators
        self.error_patterns = [
            r'wait,?\s+that\'s wrong',
            r'actually,?\s+no',
            r'mistake|error|incorrect|wrong',
            r'let me correct|fix that',
            r'oops|oh wait',
        ]
        
        # Breakthrough/insight indicators
        self.breakthrough_patterns = [
            r'ah!|aha!|oh!|i see!',
            r'that means|this shows|now i understand',
            r'the key is|the answer is|breakthrough',
            r'finally|got it|makes sense now',
        ]
        
        # Concept extraction patterns
        self.concept_patterns = [
            r'concept of \w+',
            r'principle of \w+',
            r'theory of \w+',
            r'idea of \w+',
            r'notion of \w+',
        ]
    
    def parse_thinking_content(self, content: str) -> ThinkingFlow:
        """
        Parse complete thinking content and extract comprehensive insights
        """
        
        # Split into reasoning segments
        segments = self._segment_thinking(content)
        
        # Analyze each segment
        insights = []
        emotional_journey = []
        key_breakthroughs = []
        potential_issues = []
        
        for segment in segments:
            insight = self._analyze_segment(segment)
            insights.append(insight)
            emotional_journey.append(insight.emotion)
            
            # Check for breakthroughs
            if self._contains_breakthrough(segment):
                key_breakthroughs.append(segment[:100] + "...")
            
            # Check for issues
            if insight.contains_error or insight.confidence < 0.3:
                potential_issues.append(f"Low confidence or error in: {segment[:50]}...")
        
        # Extract final answer from content
        final_answer = self._extract_final_answer(content)
        
        # Calculate overall metrics
        overall_confidence = self._calculate_overall_confidence(insights)
        coherence = self._calculate_coherence(insights)
        final_confidence = self._map_confidence_level(overall_confidence)
        
        # Assess reasoning quality
        reasoning_quality = self._assess_reasoning_quality(insights, final_answer)
        
        # Determine if reasoning should be shown
        should_show_reasoning = len(insights) > 1 and reasoning_quality in ['excellent', 'good']
        
        return ThinkingFlow(
            insights=insights,
            final_answer=final_answer,
            overall_confidence=overall_confidence,
            reasoning_coherence=coherence,
            emotional_journey=emotional_journey,
            key_breakthroughs=key_breakthroughs,
            potential_issues=potential_issues,
            final_confidence=final_confidence,
            confidence_score=overall_confidence,  # Compatibility alias
            reasoning_quality=reasoning_quality,
            should_show_reasoning=should_show_reasoning,
            emotion_progression=emotional_journey  # Compatibility alias
        )
    
    def _segment_thinking(self, content: str) -> List[str]:
        """Split thinking content into logical segments"""
        
        # Split by sentences, but keep segments that flow together
        sentences = re.split(r'[.!?]+', content)
        
        segments = []
        current_segment = ""
        
        for sentence in sentences:
            sentence = sentence.strip()
            if not sentence:
                continue
                
            # Add to current segment
            if current_segment:
                current_segment += ". " + sentence
            else:
                current_segment = sentence
            
            # Check if this is a natural break point
            if (len(current_segment) > 100 or 
                self._is_segment_boundary(sentence) or
                len(sentences) == 1):  # Single sentence
                
                segments.append(current_segment)
                current_segment = ""
        
        # Add any remaining content
        if current_segment:
            segments.append(current_segment)
        
        return [seg for seg in segments if len(seg.strip()) > 10]
    
    def _is_segment_boundary(self, sentence: str) -> bool:
        """Determine if this sentence ends a reasoning segment"""
        sentence_lower = sentence.lower()
        
        boundary_indicators = [
            'so', 'therefore', 'thus', 'hence',
            'next', 'then', 'now',
            'but', 'however', 'although',
            'in conclusion', 'finally'
        ]
        
        return any(sentence_lower.startswith(indicator) for indicator in boundary_indicators)
    
    def _analyze_segment(self, segment: str) -> ReasoningInsight:
        """Analyze individual reasoning segment"""
        
        segment_lower = segment.lower()
        
        # Determine reasoning type
        reasoning_type = self._classify_reasoning_type(segment)
        
        # Extract confidence
        confidence = self._extract_confidence(segment)
        
        # Extract emotion
        emotion = self._extract_emotion(segment)
        
        # Calculate complexity
        complexity = self._calculate_complexity(segment)
        
        # Check for errors
        contains_error = self._contains_error(segment)
        
        # Extract key concepts
        key_concepts = self._extract_concepts(segment)
        
        return ReasoningInsight(
            content=segment,
            reasoning_type=reasoning_type,
            confidence=confidence,
            emotion=emotion,
            complexity_score=complexity,
            contains_error=contains_error,
            key_concepts=key_concepts
        )
    
    def _classify_reasoning_type(self, segment: str) -> ReasoningType:
        """Classify the type of reasoning in this segment"""
        segment_lower = segment.lower()
        
        # Score each type
        scores = {
            ReasoningType.MATHEMATICAL: self._pattern_score(segment_lower, self.math_patterns),
            ReasoningType.LOGICAL: self._pattern_score(segment_lower, self.logic_patterns),
            ReasoningType.CREATIVE: self._pattern_score(segment_lower, self.creative_patterns),
            ReasoningType.ANALYTICAL: self._pattern_score(segment_lower, self.analytical_patterns),
        }
        
        # Default to conversational if no strong patterns
        max_score = max(scores.values())
        if max_score < 0.3:
            return ReasoningType.CONVERSATIONAL
        
        return max(scores, key=scores.get)
    
    def _pattern_score(self, text: str, patterns: List[str]) -> float:
        """Score how well text matches a set of patterns"""
        matches = 0
        total_patterns = len(patterns)
        
        for pattern in patterns:
            if re.search(pattern, text):
                matches += 1
        
        return matches / total_patterns if total_patterns > 0 else 0.0
    
    def _extract_confidence(self, segment: str) -> float:
        """Extract confidence level from segment"""
        segment_lower = segment.lower()
        
        # Check each confidence level
        for level, patterns in self.confidence_indicators.items():
            for pattern in patterns:
                if re.search(pattern, segment_lower):
                    return {
                        'very_high': 0.95,
                        'high': 0.8,
                        'medium': 0.6,
                        'low': 0.4,
                        'very_low': 0.2
                    }[level]
        
        # Default medium confidence
        return 0.6
    
    def _extract_emotion(self, segment: str) -> str:
        """Extract emotional tone from segment"""
        segment_lower = segment.lower()
        
        emotion_scores = {}
        for emotion, patterns in self.emotion_patterns.items():
            score = self._pattern_score(segment_lower, patterns)
            if score > 0:
                emotion_scores[emotion] = score
        
        if emotion_scores:
            return max(emotion_scores, key=emotion_scores.get)
        
        return "thinking"  # Default
    
    def _calculate_complexity(self, segment: str) -> float:
        """Calculate reasoning complexity score (0-1)"""
        
        # Factors that increase complexity
        complexity_factors = 0
        
        # Length factor (longer reasoning tends to be more complex)
        word_count = len(segment.split())
        if word_count > 20:
            complexity_factors += 0.2
        if word_count > 50:
            complexity_factors += 0.2
        
        # Multiple clauses/sentences
        sentence_count = len(re.split(r'[.!?]+', segment))
        if sentence_count > 2:
            complexity_factors += 0.2
        
        # Conditional reasoning
        if re.search(r'if\s+.*\s+then|suppose|assume', segment.lower()):
            complexity_factors += 0.2
        
        # Mathematical operations
        if re.search(r'\d+.*[+\-*/].*\d+', segment):
            complexity_factors += 0.2
        
        # Multiple concepts
        concept_count = len(re.findall(r'\b[A-Z][a-z]+\b', segment))
        if concept_count > 3:
            complexity_factors += 0.2
        
        return min(complexity_factors, 1.0)
    
    def _contains_error(self, segment: str) -> bool:
        """Check if segment contains error indicators"""
        segment_lower = segment.lower()
        
        for pattern in self.error_patterns:
            if re.search(pattern, segment_lower):
                return True
        
        return False
    
    def _contains_breakthrough(self, segment: str) -> bool:
        """Check if segment contains breakthrough/insight indicators"""
        segment_lower = segment.lower()
        
        for pattern in self.breakthrough_patterns:
            if re.search(pattern, segment_lower):
                return True
        
        return False
    
    def _extract_concepts(self, segment: str) -> List[str]:
        """Extract key concepts from segment"""
        concepts = []
        
        # Extract using concept patterns
        for pattern in self.concept_patterns:
            matches = re.findall(pattern, segment.lower())
            concepts.extend(matches)
        
        # Extract capitalized words (likely concepts)
        capitalized = re.findall(r'\b[A-Z][a-z]+\b', segment)
        concepts.extend(capitalized)
        
        # Extract quoted terms
        quoted = re.findall(r'"([^"]*)"', segment)
        concepts.extend(quoted)
        
        # Remove duplicates and filter short terms
        unique_concepts = list(set(concepts))
        return [c for c in unique_concepts if len(c) > 2]
    
    def _calculate_overall_confidence(self, insights: List[ReasoningInsight]) -> float:
        """Calculate overall confidence from all insights"""
        if not insights:
            return 0.5
        
        total_confidence = sum(insight.confidence for insight in insights)
        base_confidence = total_confidence / len(insights)
        
        # Penalize if any insights show errors
        error_penalty = sum(0.2 for insight in insights if insight.contains_error)
        
        return max(0.0, min(1.0, base_confidence - error_penalty))
    
    def _calculate_coherence(self, insights: List[ReasoningInsight]) -> float:
        """Calculate reasoning coherence score"""
        if len(insights) < 2:
            return 1.0  # Single insight is perfectly coherent
        
        coherence_score = 1.0
        
        # Check for consistency in reasoning types
        reasoning_types = [insight.reasoning_type for insight in insights]
        type_consistency = len(set(reasoning_types)) / len(reasoning_types)
        coherence_score *= (0.5 + 0.5 * type_consistency)
        
        # Check for logical progression (increasing confidence over time)
        confidences = [insight.confidence for insight in insights]
        if len(confidences) > 1:
            # Prefer increasing or stable confidence
            progression_score = 1.0
            for i in range(1, len(confidences)):
                if confidences[i] < confidences[i-1] - 0.2:  # Significant drop
                    progression_score *= 0.8
            coherence_score *= progression_score
        
        return max(0.0, min(1.0, coherence_score))
    
    def _extract_final_answer(self, content: str) -> str:
        """Extract the final answer from thinking content"""
        
        # Remove thinking tags first
        clean_content = re.sub(r'<think>.*?</think>', '', content, flags=re.DOTALL)
        
        # Look for explicit answer patterns
        answer_patterns = [
            r'(?:final answer|the answer is|answer):?\s*(.+?)(?:\.|$)',
            r'(?:therefore|so|conclusion|result):?\s*(.+?)(?:\.|$)',
        ]
        
        for pattern in answer_patterns:
            match = re.search(pattern, clean_content, re.IGNORECASE | re.DOTALL)
            if match:
                answer = match.group(1).strip()
                if len(answer) > 3:  # Reasonable length
                    return answer
        
        # Fallback: take the last substantial sentence
        sentences = re.split(r'[.!?]+', clean_content.strip())
        for sentence in reversed(sentences):
            sentence = sentence.strip()
            if len(sentence) > 10 and not any(word in sentence.lower() 
                                            for word in ['let me', 'hmm', 'wait', 'think']):
                return sentence
        
        # Ultimate fallback
        return clean_content.strip()[:100] if clean_content.strip() else "No clear answer found"
    
    def _assess_reasoning_quality(self, insights: List[ReasoningInsight], final_answer: str) -> str:
        """Assess overall quality of reasoning process"""
        
        if not insights:
            return "poor"
        
        # Calculate quality score
        quality_score = 0
        
        # Check for good reasoning progression
        has_analysis = any(insight.reasoning_type == ReasoningType.ANALYTICAL for insight in insights)
        has_logical = any(insight.reasoning_type == ReasoningType.LOGICAL for insight in insights)
        has_mathematical = any(insight.reasoning_type == ReasoningType.MATHEMATICAL for insight in insights)
        
        if has_analysis: quality_score += 1
        if has_logical: quality_score += 1
        if has_mathematical: quality_score += 1
        
        # Check confidence progression
        avg_confidence = sum(insight.confidence for insight in insights) / len(insights)
        if avg_confidence > 0.7: quality_score += 1
        if avg_confidence > 0.8: quality_score += 1
        
        # Check answer quality
        if len(final_answer) > 10: quality_score += 1
        if not any(indicator in final_answer.lower() for indicator in ['confused', 'not sure', 'unclear']):
            quality_score += 1
        
        # Map score to quality
        if quality_score >= 5:
            return "excellent"
        elif quality_score >= 3:
            return "good"
        elif quality_score >= 2:
            return "fair"
        else:
            return "poor"
    
    def _map_confidence_level(self, confidence: float) -> ConfidenceLevel:
        """Map numeric confidence to confidence level enum"""
        if confidence >= 0.9:
            return ConfidenceLevel.VERY_HIGH
        elif confidence >= 0.7:
            return ConfidenceLevel.HIGH
        elif confidence >= 0.5:
            return ConfidenceLevel.MEDIUM
        elif confidence >= 0.3:
            return ConfidenceLevel.LOW
        else:
            return ConfidenceLevel.VERY_LOW
    
    def get_reasoning_summary(self, thinking_flow: ThinkingFlow) -> Dict:
        """Generate a summary of the reasoning process"""
        
        # Count reasoning types
        type_counts = {}
        for insight in thinking_flow.insights:
            rtype = insight.reasoning_type.value
            type_counts[rtype] = type_counts.get(rtype, 0) + 1
        
        # Get dominant emotion
        emotion_counts = {}
        for emotion in thinking_flow.emotional_journey:
            emotion_counts[emotion] = emotion_counts.get(emotion, 0) + 1
        dominant_emotion = max(emotion_counts, key=emotion_counts.get) if emotion_counts else "neutral"
        
        return {
            "total_segments": len(thinking_flow.insights),
            "overall_confidence": thinking_flow.overall_confidence,
            "coherence_score": thinking_flow.reasoning_coherence,
            "final_confidence_level": thinking_flow.final_confidence.value,
            "dominant_reasoning_type": max(type_counts, key=type_counts.get) if type_counts else "conversational",
            "dominant_emotion": dominant_emotion,
            "breakthrough_count": len(thinking_flow.key_breakthroughs),
            "issue_count": len(thinking_flow.potential_issues),
            "average_complexity": sum(i.complexity_score for i in thinking_flow.insights) / len(thinking_flow.insights) if thinking_flow.insights else 0,
            "reasoning_types": type_counts,
            "emotional_journey": thinking_flow.emotional_journey
        }

# Convenience function for quick parsing
def parse_thinking(content: str) -> Dict:
    """Quick thinking analysis"""
    parser = ThinkingContentParser()
    flow = parser.parse_thinking_content(content)
    return parser.get_reasoning_summary(flow)