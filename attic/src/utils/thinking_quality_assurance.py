#!/usr/bin/env python3
"""
M1K3 Thinking Quality Assurance System
Validates thinking processes and provides fallback mechanisms for reliability
"""

import re
import time
from typing import Dict, List, Optional, Tuple, Union
from dataclasses import dataclass
from enum import Enum

from src.utils.thinking_parser import ThinkingContentParser, ThinkingFlow, ConfidenceLevel

class QualityIssue(Enum):
    """Types of quality issues that can be detected"""
    CIRCULAR_REASONING = "circular_reasoning"
    CONTRADICTORY_STATEMENTS = "contradictory_statements"
    EXCESSIVE_UNCERTAINTY = "excessive_uncertainty"
    INCOMPLETE_REASONING = "incomplete_reasoning"
    MATHEMATICAL_ERROR = "mathematical_error"
    LOGICAL_FALLACY = "logical_fallacy"
    RAMBLING_RESPONSE = "rambling_response"
    HALLUCINATION_DETECTED = "hallucination_detected"
    CONTEXT_CONFUSION = "context_confusion"

class FallbackStrategy(Enum):
    """Strategies for handling quality issues"""
    RETRY_WITH_CONSTRAINTS = "retry_with_constraints"
    EXTRACT_CORE_ANSWER = "extract_core_answer"
    USE_DIRECT_MODE = "use_direct_mode"
    REQUEST_CLARIFICATION = "request_clarification"
    PROVIDE_SAFE_RESPONSE = "provide_safe_response"

@dataclass
class QualityAssessment:
    """Assessment of response quality"""
    overall_score: float  # 0-1
    confidence_level: ConfidenceLevel
    issues_detected: List[QualityIssue]
    reasoning_coherence: float
    factual_consistency: float
    completeness_score: float
    recommended_action: FallbackStrategy
    safe_response_available: bool
    extraction_possible: bool

class ThinkingQualityAssurance:
    """
    Quality assurance system for thinking mode responses
    """
    
    def __init__(self):
        self.thinking_parser = ThinkingContentParser()
        
        # Quality thresholds
        self.quality_thresholds = {
            "minimum_acceptable": 0.6,
            "good_quality": 0.8,
            "excellent_quality": 0.9,
            "max_uncertainty_ratio": 0.3,
            "min_confidence_score": 0.4,
            "max_response_length": 1000,
            "min_response_length": 10,
        }
        
        # Mathematical validation patterns
        self.math_validation_patterns = [
            (r'(\d+)\s*\+\s*(\d+)\s*=\s*(\d+)', self._validate_addition),
            (r'(\d+)\s*-\s*(\d+)\s*=\s*(\d+)', self._validate_subtraction),
            (r'(\d+)\s*\*\s*(\d+)\s*=\s*(\d+)', self._validate_multiplication),
            (r'(\d+)\s*/\s*(\d+)\s*=\s*(\d+)', self._validate_division),
        ]
        
        # Common logical fallacy patterns
        self.fallacy_patterns = [
            (r'all .* are .* because .* is .*', "hasty_generalization"),
            (r'if .* then .* because .* always .*', "false_cause"),
            (r'.* is wrong because .* said it', "ad_hominem"),
            (r'everyone knows that .*', "appeal_to_popularity"),
        ]
        
        # Hallucination indicators (context-aware)
        self.hallucination_indicators = [
            r'as we discussed earlier',
            r'in our previous conversation',
            r'you mentioned that',
            r'referring back to',
            r'as you said',
            r'based on what you told me',
        ]
        
        # Circular reasoning patterns
        self.circular_reasoning_patterns = [
            r'(.*) because \1',
            r'(.*) is true because (.*) is true because \1',
            r'this is correct because it\'s right',
        ]
        
        # Safe fallback responses by topic
        self.safe_responses = {
            "mathematical": "Let me solve this step by step: {calculation}",
            "logical": "Based on the given information, we can conclude: {conclusion}",
            "creative": "Here's a creative response: {creative_output}",
            "factual": "The answer is: {fact}",
            "conversational": "I understand your question. Let me provide a helpful response.",
            "unknown": "I'll do my best to answer your question accurately."
        }
    
    def assess_thinking_quality(self, thinking_content: str, original_query: str) -> QualityAssessment:
        """
        Comprehensive quality assessment of thinking content
        """
        
        # Parse thinking content
        thinking_flow = self.thinking_parser.parse_thinking_content(thinking_content)
        
        # Detect various quality issues
        issues = []
        
        # Check for circular reasoning
        if self._has_circular_reasoning(thinking_content):
            issues.append(QualityIssue.CIRCULAR_REASONING)
        
        # Check for contradictions
        if self._has_contradictions(thinking_flow):
            issues.append(QualityIssue.CONTRADICTORY_STATEMENTS)
        
        # Check for excessive uncertainty
        if self._has_excessive_uncertainty(thinking_flow):
            issues.append(QualityIssue.EXCESSIVE_UNCERTAINTY)
        
        # Check for mathematical errors
        if self._has_mathematical_errors(thinking_content):
            issues.append(QualityIssue.MATHEMATICAL_ERROR)
        
        # Check for logical fallacies
        if self._has_logical_fallacies(thinking_content):
            issues.append(QualityIssue.LOGICAL_FALLACY)
        
        # Check for hallucinations
        if self._has_hallucinations(thinking_content, original_query):
            issues.append(QualityIssue.HALLUCINATION_DETECTED)
        
        # Check for rambling
        if self._is_rambling(thinking_content):
            issues.append(QualityIssue.RAMBLING_RESPONSE)
        
        # Check for incomplete reasoning
        if self._is_incomplete_reasoning(thinking_flow):
            issues.append(QualityIssue.INCOMPLETE_REASONING)
        
        # Calculate overall quality metrics
        overall_score = self._calculate_overall_score(thinking_flow, issues)
        factual_consistency = self._assess_factual_consistency(thinking_content)
        completeness_score = self._assess_completeness(thinking_flow, original_query)
        
        # Determine recommended action
        recommended_action = self._determine_fallback_strategy(overall_score, issues, thinking_flow)
        
        # Check if safe response is available
        safe_response_available = self._can_provide_safe_response(original_query)
        extraction_possible = self._can_extract_core_answer(thinking_content)
        
        return QualityAssessment(
            overall_score=overall_score,
            confidence_level=thinking_flow.final_confidence,
            issues_detected=issues,
            reasoning_coherence=thinking_flow.reasoning_coherence,
            factual_consistency=factual_consistency,
            completeness_score=completeness_score,
            recommended_action=recommended_action,
            safe_response_available=safe_response_available,
            extraction_possible=extraction_possible
        )
    
    def apply_quality_assurance(self, thinking_content: str, original_query: str) -> Tuple[str, bool, List[str]]:
        """
        Apply quality assurance and return improved response
        Returns: (final_response, is_safe, warnings)
        """
        
        assessment = self.assess_thinking_quality(thinking_content, original_query)
        warnings = []
        
        # If quality is acceptable, proceed with minor cleanup
        if assessment.overall_score >= self.quality_thresholds["minimum_acceptable"]:
            final_response = self._cleanup_response(thinking_content)
            is_safe = True
            
            # Add warnings for minor issues
            if assessment.issues_detected:
                warnings.append(f"Minor issues detected: {[issue.value for issue in assessment.issues_detected]}")
            
            return final_response, is_safe, warnings
        
        # Quality is below threshold - apply fallback strategy
        warnings.append(f"Quality score {assessment.overall_score:.2f} below threshold")
        
        if assessment.recommended_action == FallbackStrategy.EXTRACT_CORE_ANSWER:
            if assessment.extraction_possible:
                final_response = self._extract_core_answer(thinking_content)
                return final_response, True, warnings + ["Extracted core answer from reasoning"]
        
        elif assessment.recommended_action == FallbackStrategy.PROVIDE_SAFE_RESPONSE:
            if assessment.safe_response_available:
                final_response = self._generate_safe_response(original_query)
                return final_response, True, warnings + ["Used safe fallback response"]
        
        elif assessment.recommended_action == FallbackStrategy.REQUEST_CLARIFICATION:
            final_response = "I need to think about this more carefully. Could you rephrase your question?"
            return final_response, True, warnings + ["Requested clarification due to quality issues"]
        
        # Default fallback
        final_response = "I apologize, but I need to approach this question differently. Let me give you a direct answer."
        return final_response, False, warnings + ["Quality assurance triggered fallback"]
    
    def _has_circular_reasoning(self, content: str) -> bool:
        """Detect circular reasoning patterns"""
        content_lower = content.lower()
        
        for pattern in self.circular_reasoning_patterns:
            if re.search(pattern, content_lower):
                return True
        
        return False
    
    def _has_contradictions(self, thinking_flow: ThinkingFlow) -> bool:
        """Detect contradictory statements in reasoning"""
        
        # Look for opposing statements in different insights
        insights = thinking_flow.insights
        
        for i, insight1 in enumerate(insights):
            for insight2 in insights[i+1:]:
                if self._are_contradictory(insight1.content, insight2.content):
                    return True
        
        return False
    
    def _are_contradictory(self, statement1: str, statement2: str) -> bool:
        """Check if two statements contradict each other"""
        
        # Simple contradiction patterns
        contradiction_pairs = [
            (r'\bis\b', r'\bis not\b'),
            (r'\bcan\b', r'\bcannot\b'),
            (r'\bwill\b', r'\bwill not\b'),
            (r'\btrue\b', r'\bfalse\b'),
            (r'\byes\b', r'\bno\b'),
        ]
        
        s1_lower = statement1.lower()
        s2_lower = statement2.lower()
        
        for pos_pattern, neg_pattern in contradiction_pairs:
            if (re.search(pos_pattern, s1_lower) and re.search(neg_pattern, s2_lower)) or \
               (re.search(neg_pattern, s1_lower) and re.search(pos_pattern, s2_lower)):
                return True
        
        return False
    
    def _has_excessive_uncertainty(self, thinking_flow: ThinkingFlow) -> bool:
        """Check if reasoning shows excessive uncertainty"""
        
        if not thinking_flow.insights:
            return True
        
        uncertain_count = sum(1 for insight in thinking_flow.insights 
                            if insight.confidence < 0.4)
        
        uncertainty_ratio = uncertain_count / len(thinking_flow.insights)
        
        return uncertainty_ratio > self.quality_thresholds["max_uncertainty_ratio"]
    
    def _has_mathematical_errors(self, content: str) -> bool:
        """Validate mathematical calculations in content"""
        
        for pattern, validator in self.math_validation_patterns:
            matches = re.finditer(pattern, content)
            for match in matches:
                if not validator(match):
                    return True
        
        return False
    
    def _validate_addition(self, match) -> bool:
        """Validate addition operation"""
        try:
            a, b, result = map(int, match.groups())
            return a + b == result
        except:
            return False
    
    def _validate_subtraction(self, match) -> bool:
        """Validate subtraction operation"""
        try:
            a, b, result = map(int, match.groups())
            return a - b == result
        except:
            return False
    
    def _validate_multiplication(self, match) -> bool:
        """Validate multiplication operation"""
        try:
            a, b, result = map(int, match.groups())
            return a * b == result
        except:
            return False
    
    def _validate_division(self, match) -> bool:
        """Validate division operation"""
        try:
            a, b, result = map(int, match.groups())
            return b != 0 and a / b == result
        except:
            return False
    
    def _has_logical_fallacies(self, content: str) -> bool:
        """Detect common logical fallacies"""
        content_lower = content.lower()
        
        for pattern, fallacy_type in self.fallacy_patterns:
            if re.search(pattern, content_lower):
                return True
        
        return False
    
    def _has_hallucinations(self, content: str, original_query: str) -> bool:
        """Detect hallucinations (references to non-existent context)"""
        content_lower = content.lower()
        
        for indicator in self.hallucination_indicators:
            if indicator in content_lower:
                return True
        
        return False
    
    def _is_rambling(self, content: str) -> bool:
        """Check if response is rambling/too verbose"""
        
        word_count = len(content.split())
        sentence_count = len(re.split(r'[.!?]+', content))
        
        # Check various rambling indicators
        too_long = word_count > self.quality_thresholds["max_response_length"]
        too_many_sentences = sentence_count > 10
        repetitive = self._has_repetitive_phrases(content)
        
        return too_long or too_many_sentences or repetitive
    
    def _has_repetitive_phrases(self, content: str) -> bool:
        """Check for repetitive phrases that indicate rambling"""
        
        # Split into sentences and check for repetition
        sentences = re.split(r'[.!?]+', content)
        
        for i, sentence in enumerate(sentences):
            for other_sentence in sentences[i+1:]:
                if len(sentence) > 20 and sentence.strip() == other_sentence.strip():
                    return True
        
        return False
    
    def _is_incomplete_reasoning(self, thinking_flow: ThinkingFlow) -> bool:
        """Check if reasoning is incomplete"""
        
        if not thinking_flow.insights:
            return True
        
        # Check if final answer is too short or missing
        if len(thinking_flow.final_answer.strip()) < self.quality_thresholds["min_response_length"]:
            return True
        
        # Check if reasoning has logical flow
        has_analysis = any(insight.reasoning_type.value in ["analytical", "logical"] 
                          for insight in thinking_flow.insights)
        
        return not has_analysis
    
    def _calculate_overall_score(self, thinking_flow: ThinkingFlow, issues: List[QualityIssue]) -> float:
        """Calculate overall quality score"""
        
        base_score = thinking_flow.overall_confidence
        
        # Penalize for each issue
        issue_penalty = len(issues) * 0.1
        
        # Bonus for good coherence
        coherence_bonus = thinking_flow.reasoning_coherence * 0.2
        
        # Penalize for very low confidence
        if thinking_flow.final_confidence == ConfidenceLevel.VERY_LOW:
            confidence_penalty = 0.3
        elif thinking_flow.final_confidence == ConfidenceLevel.LOW:
            confidence_penalty = 0.1
        else:
            confidence_penalty = 0
        
        final_score = base_score + coherence_bonus - issue_penalty - confidence_penalty
        
        return max(0.0, min(1.0, final_score))
    
    def _assess_factual_consistency(self, content: str) -> float:
        """Assess factual consistency of content"""
        
        # Basic consistency checks
        consistency_score = 1.0
        
        # Check for self-contradictions
        if self._has_self_contradictions(content):
            consistency_score -= 0.3
        
        # Check for impossible claims
        if self._has_impossible_claims(content):
            consistency_score -= 0.2
        
        return max(0.0, consistency_score)
    
    def _has_self_contradictions(self, content: str) -> bool:
        """Check for contradictions within the same response"""
        # Simplified check - could be enhanced with NLP
        sentences = re.split(r'[.!?]+', content)
        
        for i, sent1 in enumerate(sentences):
            for sent2 in sentences[i+1:]:
                if self._are_contradictory(sent1, sent2):
                    return True
        
        return False
    
    def _has_impossible_claims(self, content: str) -> bool:
        """Check for obviously impossible claims"""
        
        impossible_patterns = [
            r'\d{5,}\s*\+\s*\d{5,}\s*=\s*\d{1,3}',  # Large numbers adding to small result
            r'before (1900|1800|1700).*computer',     # Anachronistic technology
            r'humans.*fly.*without.*wings.*machine',  # Impossible human abilities
        ]
        
        for pattern in impossible_patterns:
            if re.search(pattern, content.lower()):
                return True
        
        return False
    
    def _assess_completeness(self, thinking_flow: ThinkingFlow, original_query: str) -> float:
        """Assess how completely the response addresses the query"""
        
        # Extract key terms from query
        query_terms = set(word.lower() for word in re.findall(r'\b\w+\b', original_query) 
                         if len(word) > 2)
        
        # Extract terms from final answer
        answer_terms = set(word.lower() for word in re.findall(r'\b\w+\b', thinking_flow.final_answer)
                          if len(word) > 2)
        
        # Calculate overlap
        if not query_terms:
            return 1.0
        
        overlap = len(query_terms & answer_terms) / len(query_terms)
        
        # Adjust based on answer length
        answer_length_factor = min(1.0, len(thinking_flow.final_answer) / 50)
        
        return overlap * answer_length_factor
    
    def _determine_fallback_strategy(self, overall_score: float, issues: List[QualityIssue], 
                                   thinking_flow: ThinkingFlow) -> FallbackStrategy:
        """Determine the best fallback strategy"""
        
        # If score is very low, use safe response
        if overall_score < 0.3:
            return FallbackStrategy.PROVIDE_SAFE_RESPONSE
        
        # If mathematical errors, try extraction
        if QualityIssue.MATHEMATICAL_ERROR in issues:
            return FallbackStrategy.EXTRACT_CORE_ANSWER
        
        # If hallucinations detected, request clarification
        if QualityIssue.HALLUCINATION_DETECTED in issues:
            return FallbackStrategy.REQUEST_CLARIFICATION
        
        # If rambling, extract core
        if QualityIssue.RAMBLING_RESPONSE in issues:
            return FallbackStrategy.EXTRACT_CORE_ANSWER
        
        # If incomplete, retry with constraints
        if QualityIssue.INCOMPLETE_REASONING in issues:
            return FallbackStrategy.RETRY_WITH_CONSTRAINTS
        
        # Default to extraction
        return FallbackStrategy.EXTRACT_CORE_ANSWER
    
    def _can_provide_safe_response(self, query: str) -> bool:
        """Check if we can provide a safe fallback response"""
        
        # Simple categorization
        query_lower = query.lower()
        
        safe_categories = [
            "mathematical", "logical", "factual", "conversational"
        ]
        
        for category in safe_categories:
            if any(term in query_lower for term in self._get_category_terms(category)):
                return True
        
        return False
    
    def _get_category_terms(self, category: str) -> List[str]:
        """Get terms that indicate a specific category"""
        
        category_terms = {
            "mathematical": ["add", "subtract", "multiply", "divide", "calculate", "math", "equals"],
            "logical": ["if", "then", "because", "therefore", "conclude", "logic"],
            "factual": ["what is", "who is", "when", "where", "define", "capital"],
            "conversational": ["hello", "hi", "how are you", "thanks", "goodbye"]
        }
        
        return category_terms.get(category, [])
    
    def _can_extract_core_answer(self, content: str) -> bool:
        """Check if we can extract a core answer from thinking content"""
        
        # Look for clear answer patterns
        answer_patterns = [
            r'the answer is:?\s*(.+)',
            r'therefore,?\s*(.+)',
            r'so,?\s*(.+)',
            r'conclusion:?\s*(.+)',
            r'result:?\s*(.+)',
        ]
        
        for pattern in answer_patterns:
            if re.search(pattern, content.lower()):
                return True
        
        return False
    
    def _cleanup_response(self, content: str) -> str:
        """Clean up response for final output"""
        
        # Remove thinking tags
        content = re.sub(r'<think>.*?</think>', '', content, flags=re.DOTALL)
        
        # Clean up whitespace
        content = re.sub(r'\s+', ' ', content).strip()
        
        # Remove common artifacts
        artifacts = [
            "let me think about this",
            "hmm,",
            "wait,",
            "actually,",
            "final answer:",
            "answer:",
        ]
        
        for artifact in artifacts:
            content = re.sub(rf'^{re.escape(artifact)}\s*', '', content, flags=re.IGNORECASE)
        
        return content.strip()
    
    def _extract_core_answer(self, content: str) -> str:
        """Extract the core answer from thinking content"""
        
        # Try to find explicit answer markers
        answer_patterns = [
            r'(?:the answer is|therefore|so|conclusion|result):?\s*(.+?)(?:\.|$)',
            r'(?:final answer|answer):?\s*(.+?)(?:\.|$)',
        ]
        
        for pattern in answer_patterns:
            match = re.search(pattern, content, re.IGNORECASE | re.DOTALL)
            if match:
                answer = match.group(1).strip()
                if len(answer) > 5:  # Reasonable length
                    return self._cleanup_response(answer)
        
        # Fallback: take last sentence
        sentences = re.split(r'[.!?]+', content)
        for sentence in reversed(sentences):
            sentence = sentence.strip()
            if len(sentence) > 10 and not any(word in sentence.lower() 
                                            for word in ['let me', 'hmm', 'wait']):
                return sentence
        
        return "I need to think about this more carefully."
    
    def _generate_safe_response(self, query: str) -> str:
        """Generate a safe fallback response"""
        
        query_lower = query.lower()
        
        # Categorize query and provide appropriate safe response
        if any(term in query_lower for term in self._get_category_terms("mathematical")):
            return "I'll solve this mathematical problem step by step."
        
        elif any(term in query_lower for term in self._get_category_terms("logical")):
            return "Let me work through this logical problem systematically."
        
        elif any(term in query_lower for term in self._get_category_terms("factual")):
            return "Let me provide you with accurate information."
        
        else:
            return "I'll do my best to give you a helpful and accurate response."

# Convenience function
def validate_thinking_response(thinking_content: str, original_query: str) -> Tuple[str, bool, List[str]]:
    """Quick validation of thinking response"""
    qa_system = ThinkingQualityAssurance()
    return qa_system.apply_quality_assurance(thinking_content, original_query)