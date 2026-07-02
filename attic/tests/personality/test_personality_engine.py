#!/usr/bin/env python3
"""
TDD Tests for M1K3 Personality Engine
Testing humor effectiveness, context-aware selection, and brand consistency
"""

import pytest
import sys
from pathlib import Path
from unittest.mock import Mock, patch
from datetime import datetime

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

class TestPersonalityEngine:
    """Test suite for the PersonalityEngine class"""

    def test_humor_score_calculation(self, humor_test_cases):
        """Test humor effectiveness scoring (0-10 scale)"""
        # This test will fail initially - we'll implement to make it pass
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        for test_case in humor_test_cases:
            score = engine.calculate_humor_score(
                test_case["ai_response"],
                humor_type=test_case["humor_type"],
                context=test_case["context"]
            )

            # Allow for 0.5 point variance in scoring
            assert abs(score - test_case["expected_humor_score"]) <= 0.5, \
                f"Humor score {score} not within range of expected {test_case['expected_humor_score']}"

            # Ensure score is within valid range
            assert 0 <= score <= 10, f"Humor score {score} out of valid range 0-10"

    def test_context_aware_joke_selection(self, mock_system_metrics):
        """Test joke selection based on system metrics"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        # Test battery-related humor for low battery
        low_battery_metrics = mock_system_metrics
        low_battery_metrics.battery_percent = 15

        humor = engine.select_contextual_humor(low_battery_metrics)
        assert "battery" in humor.lower() or "power" in humor.lower() or "charge" in humor.lower(), \
            "Should select battery-related humor for low battery"

        # Test CPU temperature humor for hot CPU
        hot_cpu_metrics = mock_system_metrics
        hot_cpu_metrics.cpu_temp = 85.0

        humor = engine.select_contextual_humor(hot_cpu_metrics)
        assert any(word in humor.lower() for word in ["hot", "heat", "temperature", "cool"]), \
            "Should select temperature-related humor for hot CPU"

        # Test general humor for normal conditions
        normal_metrics = mock_system_metrics
        normal_metrics.battery_percent = 75
        normal_metrics.cpu_temp = 45.0

        humor = engine.select_contextual_humor(normal_metrics)
        assert len(humor) > 0, "Should always return some humor"

    def test_personality_consistency_scoring(self, personality_consistency_tests):
        """Test M1K3 brand consistency across responses"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        for test_case in personality_consistency_tests:
            score = engine.score_personality_consistency(test_case["response"])

            # Allow for 0.5 point variance
            assert abs(score - test_case["expected_consistency"]) <= 0.5, \
                f"Consistency score {score} not within range of expected {test_case['expected_consistency']}"

            # Verify brand elements are detected correctly
            detected_elements = engine.detect_brand_elements(test_case["response"])
            expected_elements = set(test_case["brand_elements"])

            # Should detect at least 80% of expected brand elements
            if expected_elements:
                overlap = len(detected_elements.intersection(expected_elements))
                coverage = overlap / len(expected_elements)
                assert coverage >= 0.8, f"Brand element detection coverage {coverage} too low"

    def test_user_engagement_measurement(self, engagement_test_scenarios):
        """Test engagement scoring from user responses"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        for scenario in engagement_test_scenarios:
            engagement_score = engine.measure_user_engagement(
                user_response=scenario["user_response"],
                ai_response="Test response"  # Previous AI response
            )

            # Allow for 1.0 point variance in engagement scoring
            assert abs(engagement_score - scenario["expected_engagement"]) <= 1.0, \
                f"Engagement score {engagement_score} not within range of {scenario['expected_engagement']}"

            # Verify engagement indicators are detected
            detected_indicators = engine.detect_engagement_indicators(scenario["user_response"])
            expected_indicators = set(scenario["indicators"])

            # Should detect majority of expected indicators
            if expected_indicators:
                overlap = len(detected_indicators.intersection(expected_indicators))
                coverage = overlap / len(expected_indicators) if expected_indicators else 0
                assert coverage >= 0.5, f"Engagement indicator detection too low: {coverage}"

    def test_humor_appropriateness_filtering(self, mock_system_metrics):
        """Test context-based humor filtering"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        # Test that inappropriate humor is filtered out
        test_contexts = [
            {
                "name": "low_battery_emergency",
                "metrics": mock_system_metrics,
                "inappropriate_humor": ["Let's party all night!", "Time for intense gaming!"],
                "appropriate_humor": ["Better find some power soon", "Battery getting thirsty"]
            },
            {
                "name": "high_cpu_load",
                "metrics": mock_system_metrics,
                "inappropriate_humor": ["Let's run more processes!", "Time for cryptocurrency mining!"],
                "appropriate_humor": ["System working hard", "CPU earning its keep"]
            }
        ]

        for context in test_contexts:
            context["metrics"].battery_percent = 5 if "battery" in context["name"] else 75
            context["metrics"].cpu_usage = 95 if "cpu" in context["name"] else 25

            # Test inappropriate humor is filtered
            for bad_humor in context["inappropriate_humor"]:
                is_appropriate = engine.is_humor_appropriate(bad_humor, context["metrics"])
                assert not is_appropriate, f"Should filter inappropriate humor: {bad_humor}"

            # Test appropriate humor passes
            for good_humor in context["appropriate_humor"]:
                is_appropriate = engine.is_humor_appropriate(good_humor, context["metrics"])
                assert is_appropriate, f"Should allow appropriate humor: {good_humor}"

    def test_adaptive_personality_based_on_preferences(self):
        """Test personality adaptation based on user preferences"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        # Simulate user preference learning
        user_preferences = {
            "humor_intensity": 7.5,      # Moderate-high humor
            "preferred_types": ["trivia", "wordplay"],
            "avoid_types": ["self_deprecating"],
            "engagement_history": [8.0, 7.5, 9.0, 8.5]  # High engagement scores
        }

        # Test that personality adapts to preferences
        adapted_response = engine.adapt_personality_to_user(
            base_response="I can help with that.",
            user_preferences=user_preferences,
            context={}
        )

        # Should include user's preferred humor types
        humor_type = engine.detect_humor_type(adapted_response)
        if humor_type:  # If humor was added
            assert humor_type in user_preferences["preferred_types"], \
                f"Should use preferred humor type, got: {humor_type}"

        # Should avoid disliked humor types
        assert not any(avoided in adapted_response.lower()
                      for avoided in ["i'm just an ai", "i don't know much"]), \
            "Should avoid self-deprecating humor based on preferences"

    def test_personality_engine_initialization(self):
        """Test that PersonalityEngine initializes correctly"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()

        # Should have humor database loaded
        assert hasattr(engine, 'humor_database'), "Should have humor database"
        assert len(engine.humor_database) > 0, "Humor database should not be empty"

        # Should have brand elements defined
        assert hasattr(engine, 'brand_elements'), "Should have brand elements"
        assert 'm1k3_identity' in engine.brand_elements, "Should include M1K3 identity"
        assert 'local_processing' in engine.brand_elements, "Should include local processing"

        # Should have engagement indicators defined
        assert hasattr(engine, 'engagement_indicators'), "Should have engagement indicators"
        assert 'positive_emotion' in engine.engagement_indicators, "Should detect positive emotions"

    def test_humor_injection_performance(self, mock_system_metrics):
        """Test that humor injection doesn't significantly impact performance"""
        from src.personality.personality_engine import PersonalityEngine
        import time

        engine = PersonalityEngine()

        # Measure baseline response time
        start_time = time.time()
        base_response = "I can help you with that question."
        baseline_time = time.time() - start_time

        # Measure humor-enhanced response time
        start_time = time.time()
        enhanced_response = engine.enhance_response_with_personality(
            base_response=base_response,
            context=mock_system_metrics,
            user_preferences={}
        )
        enhanced_time = time.time() - start_time

        # Humor injection should add minimal overhead (< 100ms)
        time_overhead = enhanced_time - baseline_time
        assert time_overhead < 0.1, f"Humor injection overhead too high: {time_overhead}s"

        # Enhanced response should be different from base (if humor was added)
        if enhanced_response != base_response:
            assert len(enhanced_response) > len(base_response), \
                "Enhanced response should contain additional content"

class TestPersonalityDatabase:
    """Test suite for personality content database"""

    def test_humor_database_structure(self):
        """Test that humor database has correct structure"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()
        humor_db = engine.humor_database

        # Should have different humor categories
        expected_categories = [
            "battery_related", "cpu_temperature", "memory_usage",
            "trivia_facts", "ai_self_aware", "privacy_focused"
        ]

        for category in expected_categories:
            assert category in humor_db, f"Missing humor category: {category}"
            assert len(humor_db[category]) > 0, f"Empty humor category: {category}"

            # Each humor item should be a string
            for humor_item in humor_db[category]:
                assert isinstance(humor_item, str), "Humor items should be strings"
                assert len(humor_item) > 0, "Humor items should not be empty"

    def test_brand_consistency_keywords(self):
        """Test that brand elements are properly defined"""
        from src.personality.personality_engine import PersonalityEngine

        engine = PersonalityEngine()
        brand_elements = engine.brand_elements

        # Should include core M1K3 brand elements
        required_elements = [
            "m1k3_identity", "local_processing", "privacy_focused",
            "eco_conscious", "edge_ai", "zero_transmission"
        ]

        for element in required_elements:
            assert element in brand_elements, f"Missing brand element: {element}"

            # Each element should have associated keywords
            keywords = brand_elements[element]
            assert len(keywords) > 0, f"No keywords for brand element: {element}"
            assert all(isinstance(kw, str) for kw in keywords), \
                f"Keywords should be strings for element: {element}"

if __name__ == "__main__":
    pytest.main([__file__, "-v"])