#!/usr/bin/env python3
"""
TDD Integration Tests for M1K3 Personality + Analytics System
Testing end-to-end workflow from humor generation to analytics storage
"""

import pytest
import sys
from pathlib import Path
from datetime import datetime, timedelta
from unittest.mock import Mock, patch
import json

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

class TestPersonalityAnalyticsIntegration:
    """Integration tests for personality system + DuckDB analytics"""

    def test_complete_conversation_with_humor_tracking(self, temp_db, mock_system_metrics):
        """Test full conversation flow with personality analytics"""
        from src.personality.personality_engine import PersonalityEngine
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        personality_engine = PersonalityEngine()
        conversation_manager = EnhancedConversationManager(db_path=db_path)

        # Simulate a conversation flow
        user_input = "My battery is running low, what should I do?"
        base_ai_response = "You should charge your device when the battery gets low."

        # Step 1: Generate personality-enhanced response
        enhanced_response = personality_engine.enhance_response_with_personality(
            base_response=base_ai_response,
            context=mock_system_metrics,
            user_preferences={"humor_intensity": 7.0}
        )

        # Response should be enhanced with battery-related humor
        assert enhanced_response != base_ai_response, "Response should be enhanced with personality"
        assert len(enhanced_response) > len(base_ai_response), "Enhanced response should be longer"

        # Step 2: Score the humor effectiveness
        humor_score = personality_engine.calculate_humor_score(
            enhanced_response,
            humor_type="context_aware",
            context={"battery_percent": mock_system_metrics.battery_percent}
        )

        assert 0 <= humor_score <= 10, "Humor score should be in valid range"

        # Step 3: Measure personality consistency
        consistency_score = personality_engine.score_personality_consistency(enhanced_response)
        assert consistency_score >= 7.0, "Should maintain high M1K3 personality consistency"

        # Step 4: Store conversation with analytics
        from src.database.conversation_manager import ConversationRecord

        conversation = ConversationRecord(
            id="integration_test_001",
            session_id="integration_session",
            timestamp=datetime.now(),
            user_input=user_input,
            ai_response=enhanced_response,
            response_time_ms=1200,
            tokens_used=len(enhanced_response.split()),
            personality_type="witty_bartender"
        )

        humor_data = {
            "humor_score": humor_score,
            "joke_types": personality_engine.detect_humor_types(enhanced_response),
            "user_engagement_score": 8.0,  # Would be measured from user response
            "personality_consistency": consistency_score,
            "eco_credits_earned": 12
        }

        conversation_id = conversation_manager.add_conversation_with_analytics(conversation, humor_data)
        assert conversation_id is not None

        # Step 5: Verify analytics data was stored correctly
        retrieved = conversation_manager.get_conversation_with_analytics(conversation_id)
        assert retrieved is not None
        assert retrieved["humor_score"] == humor_score
        assert retrieved["personality_consistency"] == consistency_score

    def test_system_metrics_to_humor_integration(self, mock_system_metrics):
        """Test real-time system data influencing humor selection"""
        from src.personality.personality_engine import PersonalityEngine

        personality_engine = PersonalityEngine()

        # Test different system states and their humor responses
        test_scenarios = [
            {
                "name": "low_battery",
                "metrics": mock_system_metrics,
                "metric_changes": {"battery_percent": 15},
                "expected_humor_keywords": ["battery", "power", "charge", "energy"]
            },
            {
                "name": "high_cpu_temp",
                "metrics": mock_system_metrics,
                "metric_changes": {"cpu_temp": 85.0},
                "expected_humor_keywords": ["hot", "temperature", "cool", "heat"]
            },
            {
                "name": "high_memory_usage",
                "metrics": mock_system_metrics,
                "metric_changes": {"memory_percent": 95.0},
                "expected_humor_keywords": ["memory", "thinking", "brain", "remember"]
            },
            {
                "name": "normal_conditions",
                "metrics": mock_system_metrics,
                "metric_changes": {},
                "expected_humor_keywords": ["trivia", "fact", "interesting", "did you know"]
            }
        ]

        for scenario in test_scenarios:
            # Create a fresh copy of metrics for each scenario to avoid cumulative effects
            import copy
            test_metrics = copy.deepcopy(scenario["metrics"])

            # Reset to truly neutral values for all conditions first
            setattr(test_metrics, 'battery_percent', 75)    # Normal battery
            setattr(test_metrics, 'cpu_temp', 45.5)         # Normal CPU temp
            setattr(test_metrics, 'memory_percent', 60.0)   # Normal memory

            # Apply metric changes to the fresh copy
            for key, value in scenario["metric_changes"].items():
                setattr(test_metrics, key, value)

            # Generate context-aware humor
            humor = personality_engine.select_contextual_humor(test_metrics)
            assert len(humor) > 0, f"Should generate humor for {scenario['name']}"

            # Verify humor contains expected keywords
            humor_lower = humor.lower()
            keyword_found = any(keyword in humor_lower for keyword in scenario["expected_humor_keywords"])
            assert keyword_found, f"Humor should contain relevant keywords for {scenario['name']}: {humor}"

            # Verify humor is appropriate for context
            is_appropriate = personality_engine.is_humor_appropriate(humor, test_metrics)
            assert is_appropriate, f"Humor should be appropriate for {scenario['name']}: {humor}"

    def test_eco_credits_earning_workflow(self, temp_db):
        """Test environmental impact tracking through conversation"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.personality.personality_engine import PersonalityEngine

        conn, db_path = temp_db
        conversation_manager = EnhancedConversationManager(db_path=db_path)
        personality_engine = PersonalityEngine()

        session_id = "eco_credits_session"

        # Simulate a conversation session with multiple exchanges
        conversation_scenarios = [
            {"user_input": "What's the weather?", "tokens": 45, "duration_minutes": 2},
            {"user_input": "Tell me a joke", "tokens": 38, "duration_minutes": 1.5},
            {"user_input": "How do I code in Python?", "tokens": 120, "duration_minutes": 5},
            {"user_input": "What's your favorite color?", "tokens": 35, "duration_minutes": 1}
        ]

        total_conversations = 0
        total_tokens = 0
        total_duration = 0

        for i, scenario in enumerate(conversation_scenarios):
            # Create conversation with eco-friendly response
            ai_response = personality_engine.generate_eco_conscious_response(
                scenario["user_input"]
            )

            from src.database.conversation_manager import ConversationRecord
            conversation = ConversationRecord(
                id=f"eco_{i}",
                session_id=session_id,
                timestamp=datetime.now() + timedelta(minutes=total_duration),
                user_input=scenario["user_input"],
                ai_response=ai_response,
                response_time_ms=1000,
                tokens_used=scenario["tokens"],
                personality_type="eco_conscious"
            )

            # Calculate eco credits for this conversation
            eco_credits = conversation_manager.calculate_conversation_eco_credits(
                tokens_used=scenario["tokens"],
                local_processing=True,
                duration_minutes=scenario["duration_minutes"]
            )

            humor_data = {
                "humor_score": 7.0,
                "joke_types": ["eco_conscious"],
                "user_engagement_score": 8.0,
                "personality_consistency": 9.0,
                "eco_credits_earned": eco_credits
            }

            conversation_manager.add_conversation_with_analytics(conversation, humor_data)

            total_conversations += 1
            total_tokens += scenario["tokens"]
            total_duration += scenario["duration_minutes"]

        # Calculate total session eco credits
        session_eco_credits = conversation_manager.calculate_session_eco_credits(
            duration_minutes=total_duration,
            responses_generated=total_conversations,
            tokens_processed=total_tokens
        )

        # Verify eco credits calculations
        assert session_eco_credits["energy_saved_kwh"] > 0, "Should save energy vs cloud processing"
        assert session_eco_credits["water_saved_ml"] > 0, "Should save water vs data centers"
        assert session_eco_credits["carbon_reduced_kg"] > 0, "Should reduce carbon footprint"
        assert session_eco_credits["eco_score"] > 0, "Should have positive eco score"

        # Store session eco credits
        conversation_manager.add_eco_credits({
            "session_id": session_id,
            **session_eco_credits,
            "achievements": conversation_manager.check_eco_achievements(session_eco_credits)
        })

        # Verify eco achievements
        session_credits = conversation_manager.get_session_eco_credits(session_id)
        assert len(session_credits["achievements"]) > 0, "Should unlock eco achievements"

    def test_performance_optimization_feedback_loop(self, temp_db, mock_system_metrics):
        """Test performance data driving system improvements"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.personality.personality_engine import PersonalityEngine

        conn, db_path = temp_db
        conversation_manager = EnhancedConversationManager(db_path=db_path)
        personality_engine = PersonalityEngine()

        session_id = "performance_optimization_session"

        # Simulate conversation with performance tracking
        performance_scenarios = [
            {"response_time": 2500, "system_load": 85, "memory": 90, "should_optimize": True},
            {"response_time": 1200, "system_load": 45, "memory": 60, "should_optimize": False},
            {"response_time": 3000, "system_load": 95, "memory": 95, "should_optimize": True},
            {"response_time": 800, "system_load": 30, "memory": 40, "should_optimize": False},
        ]

        optimization_suggestions_found = []

        for i, scenario in enumerate(performance_scenarios):
            # Record performance metrics
            perf_data = {
                "timestamp": datetime.now(),
                "session_id": session_id,
                "response_time_ms": scenario["response_time"],
                "system_load": scenario["system_load"],
                "memory_usage": scenario["memory"],
                "model_efficiency_score": 10 - (scenario["response_time"] / 300),  # Mock efficiency
                "optimization_suggestions": []
            }

            # Generate optimization suggestions based on performance
            suggestions = conversation_manager.generate_optimization_suggestions(perf_data)

            if scenario["should_optimize"]:
                assert len(suggestions) > 0, f"Should generate suggestions for poor performance scenario {i}"
                optimization_suggestions_found.extend(suggestions)
            else:
                # Good performance may still have suggestions, but fewer
                assert len(suggestions) <= 2, f"Should have minimal suggestions for good performance scenario {i}"

            perf_data["optimization_suggestions"] = suggestions
            conversation_manager.add_performance_metrics(perf_data)

        # Verify optimization suggestions were generated for poor performance
        assert len(optimization_suggestions_found) > 0, "Should generate optimization suggestions"

        # Check common optimization suggestions
        expected_suggestions = [
            "reduce_context_length", "optimize_model_selection",
            "enable_response_caching", "monitor_system_resources"
        ]

        for suggestion in expected_suggestions:
            if suggestion in optimization_suggestions_found:
                # If found, verify it's implemented in personality engine
                assert hasattr(personality_engine, f"apply_{suggestion}"), \
                    f"PersonalityEngine should implement optimization: {suggestion}"

    def test_adaptive_personality_learning(self, temp_db):
        """Test personality adaptation based on user feedback over time"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.personality.personality_engine import PersonalityEngine

        conn, db_path = temp_db
        conversation_manager = EnhancedConversationManager(db_path=db_path)
        personality_engine = PersonalityEngine()

        session_id = "adaptive_learning_session"

        # Simulate user interaction patterns over time
        learning_scenarios = [
            # Early conversations - mixed feedback
            {"humor_type": "trivia", "user_engagement": 8.0, "feedback": "positive"},
            {"humor_type": "wordplay", "user_engagement": 6.0, "feedback": "neutral"},
            {"humor_type": "self_deprecating", "user_engagement": 3.0, "feedback": "negative"},

            # Later conversations - system learns preferences
            {"humor_type": "trivia", "user_engagement": 9.0, "feedback": "very_positive"},
            {"humor_type": "context_aware", "user_engagement": 8.5, "feedback": "positive"},
            {"humor_type": "wordplay", "user_engagement": 7.0, "feedback": "neutral"},
        ]

        user_preferences = {"humor_intensity": 7.0, "preferred_types": [], "avoid_types": []}

        for i, scenario in enumerate(learning_scenarios):
            # Generate response with current preferences
            base_response = "I can help with that."
            enhanced_response = personality_engine.adapt_personality_to_user(
                base_response=base_response,
                user_preferences=user_preferences,
                context={"humor_type": scenario["humor_type"]}
            )

            # Record conversation with feedback
            from src.database.conversation_manager import ConversationRecord
            conversation = ConversationRecord(
                id=f"learning_{i}",
                session_id=session_id,
                timestamp=datetime.now() + timedelta(minutes=i * 2),
                user_input=f"Test input {i}",
                ai_response=enhanced_response,
                response_time_ms=1000,
                tokens_used=30,
                personality_type="adaptive"
            )

            humor_data = {
                "humor_score": scenario["user_engagement"],
                "joke_types": [scenario["humor_type"]],
                "user_engagement_score": scenario["user_engagement"],
                "personality_consistency": 9.0,
                "eco_credits_earned": 10
            }

            conversation_manager.add_conversation_with_analytics(conversation, humor_data)

            # Update user preferences based on feedback
            user_preferences = personality_engine.update_user_preferences(
                user_preferences, scenario["humor_type"], scenario["user_engagement"]
            )

        # Verify learning occurred
        assert len(user_preferences["preferred_types"]) > 0, "Should learn preferred humor types"
        assert "trivia" in user_preferences["preferred_types"], "Should prefer trivia based on feedback"

        if user_preferences["avoid_types"]:
            assert "self_deprecating" in user_preferences["avoid_types"], \
                "Should avoid self-deprecating humor based on negative feedback"

    def test_real_time_analytics_dashboard_data(self, temp_db, sample_conversations):
        """Test real-time data generation for analytics dashboard"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        conversation_manager = EnhancedConversationManager(db_path=db_path)

        # Add sample conversations with analytics data
        for i, conv_data in enumerate(sample_conversations):
            from src.database.conversation_manager import ConversationRecord

            conversation = ConversationRecord(**conv_data)

            humor_data = {
                "humor_score": 7.0 + i,  # Varying scores
                "joke_types": ["trivia", "context_aware"],
                "user_engagement_score": 8.0 + i * 0.5,
                "personality_consistency": 9.0,
                "eco_credits_earned": 10 + i * 2
            }

            conversation_manager.add_conversation_with_analytics(conversation, humor_data)

        # Generate dashboard data
        dashboard_data = conversation_manager.get_dashboard_analytics(
            session_id=sample_conversations[0]["session_id"],
            hours=24
        )

        # Verify dashboard data structure
        assert "humor_metrics" in dashboard_data
        assert "engagement_metrics" in dashboard_data
        assert "eco_metrics" in dashboard_data
        assert "performance_metrics" in dashboard_data

        # Verify humor metrics
        humor_metrics = dashboard_data["humor_metrics"]
        assert "avg_humor_score" in humor_metrics
        assert "humor_trend" in humor_metrics
        assert "top_joke_types" in humor_metrics

        # Verify engagement metrics
        engagement_metrics = dashboard_data["engagement_metrics"]
        assert "avg_engagement_score" in engagement_metrics
        assert "engagement_correlation" in engagement_metrics

        # Verify eco metrics
        eco_metrics = dashboard_data["eco_metrics"]
        assert "total_eco_credits" in eco_metrics
        assert "energy_saved" in eco_metrics
        assert "carbon_reduced" in eco_metrics

class TestEndToEndWorkflow:
    """Test complete end-to-end workflow"""

    def test_complete_m1k3_conversation_workflow(self, temp_db, mock_system_metrics):
        """Test complete M1K3 conversation from input to analytics storage"""
        from src.personality.personality_engine import PersonalityEngine
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        # Initialize systems
        conn, db_path = temp_db
        personality_engine = PersonalityEngine()
        conversation_manager = EnhancedConversationManager(db_path=db_path)

        # Start session
        session_id = "complete_workflow_session"
        session = conversation_manager.start_session(session_id, "witty_bartender")

        # User input
        user_input = "I'm feeling stressed about work deadlines"

        # Generate base AI response (mock)
        base_response = "I understand work stress can be challenging. Here are some strategies that might help."

        # Enhance with personality
        enhanced_response = personality_engine.enhance_response_with_personality(
            base_response=base_response,
            context=mock_system_metrics,
            user_preferences={"humor_intensity": 6.0, "preferred_types": ["supportive", "trivia"]}
        )

        # Calculate analytics
        humor_score = personality_engine.calculate_humor_score(enhanced_response)
        consistency_score = personality_engine.score_personality_consistency(enhanced_response)
        joke_types = personality_engine.detect_humor_types(enhanced_response)

        # Store conversation with full analytics
        from src.database.conversation_manager import ConversationRecord
        conversation = ConversationRecord(
            id="complete_workflow_001",
            session_id=session_id,
            timestamp=datetime.now(),
            user_input=user_input,
            ai_response=enhanced_response,
            response_time_ms=1500,
            tokens_used=len(enhanced_response.split()),
            personality_type="witty_bartender"
        )

        # Calculate eco credits
        eco_credits = conversation_manager.calculate_conversation_eco_credits(
            tokens_used=conversation.tokens_used,
            local_processing=True,
            duration_minutes=2.0
        )

        humor_data = {
            "humor_score": humor_score,
            "joke_types": joke_types,
            "user_engagement_score": 7.5,  # Would be measured from user response
            "personality_consistency": consistency_score,
            "eco_credits_earned": eco_credits
        }

        # Store everything
        conversation_id = conversation_manager.add_conversation_with_analytics(conversation, humor_data)

        # Record performance metrics
        perf_data = {
            "timestamp": datetime.now(),
            "session_id": session_id,
            "response_time_ms": 1500,
            "system_load": mock_system_metrics.cpu_usage,
            "memory_usage": mock_system_metrics.memory_percent,
            "model_efficiency_score": 8.5,
            "optimization_suggestions": []
        }

        conversation_manager.add_performance_metrics(perf_data)

        # End session
        conversation_manager.end_session(session_id, total_queries=1, total_tokens=conversation.tokens_used)

        # Verify complete workflow
        assert conversation_id is not None, "Conversation should be stored successfully"

        # Verify all analytics are accessible
        retrieved_conversation = conversation_manager.get_conversation_with_analytics(conversation_id)
        assert retrieved_conversation is not None, "Should retrieve conversation with analytics"

        session_performance = conversation_manager.get_session_performance_metrics(session_id)
        assert len(session_performance) > 0, "Should have performance metrics"

        # Verify dashboard data is available
        dashboard_data = conversation_manager.get_dashboard_analytics(session_id, hours=1)
        assert dashboard_data is not None, "Should generate dashboard data"

        print("✅ Complete M1K3 conversation workflow test passed!")

if __name__ == "__main__":
    pytest.main([__file__, "-v"])