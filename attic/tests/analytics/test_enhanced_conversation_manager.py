#!/usr/bin/env python3
"""
TDD Tests for Enhanced DuckDB Conversation Manager
Testing personality analytics, eco-credits, and performance tracking
"""

import pytest
import sys
from pathlib import Path
from datetime import datetime, timedelta
import json

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

class TestEnhancedConversationManager:
    """Test suite for enhanced conversation manager with personality analytics"""

    def test_personality_columns_migration(self, temp_db):
        """Test adding humor/personality columns to conversations table"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Check that new columns exist
        result = conn.execute("PRAGMA table_info(conversations)").fetchall()
        column_names = [row[1] for row in result]

        expected_new_columns = [
            "humor_score", "joke_types", "user_engagement_score",
            "personality_consistency", "eco_credits_earned", "performance_metrics"
        ]

        for column in expected_new_columns:
            assert column in column_names, f"Missing new column: {column}"

        # Test that existing data is preserved during migration
        # (This will be important for production deployments)

    def test_humor_scoring_storage_and_retrieval(self, temp_db, sample_conversations):
        """Test storing and retrieving humor scores with conversations"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.database.conversation_manager import ConversationRecord

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Create enhanced conversation record with humor data
        conversation = ConversationRecord(
            id="test_humor_001",
            session_id="humor_session",
            timestamp=datetime.now(),
            user_input="Tell me a joke about computers",
            ai_response="Why do computers never get cold? Because they have Windows! Fun fact: The first computer bug was literally a bug - a moth stuck in a relay in 1947.",
            response_time_ms=1200,
            tokens_used=50,
            personality_type="witty_bartender"
        )

        # Add humor analytics data
        humor_data = {
            "humor_score": 8.5,
            "joke_types": ["wordplay", "trivia_based"],
            "user_engagement_score": 9.0,
            "personality_consistency": 9.5,
            "eco_credits_earned": 15
        }

        # Store conversation with humor data
        conversation_id = manager.add_conversation_with_analytics(conversation, humor_data)
        assert conversation_id is not None

        # Retrieve and verify humor data
        retrieved = manager.get_conversation_with_analytics(conversation_id)
        assert retrieved is not None
        assert abs(retrieved["humor_score"] - 8.5) < 0.1
        assert set(retrieved["joke_types"]) == {"wordplay", "trivia_based"}
        assert retrieved["user_engagement_score"] == 9.0
        assert retrieved["personality_consistency"] == 9.5
        assert retrieved["eco_credits_earned"] == 15

    def test_eco_credits_table_creation_and_operations(self, temp_db):
        """Test eco-credits tracking table schema and operations"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Verify eco_credits table exists and has correct schema
        result = conn.execute("PRAGMA table_info(eco_credits)").fetchall()
        column_names = [row[1] for row in result]

        expected_columns = [
            "session_id", "energy_saved_kwh", "water_saved_ml",
            "carbon_reduced_kg", "total_eco_score", "achievements"
        ]

        for column in expected_columns:
            assert column in column_names, f"Missing eco_credits column: {column}"

        # Test adding eco credits
        eco_data = {
            "session_id": "eco_test_session",
            "energy_saved_kwh": 1.25,
            "water_saved_ml": 180.0,
            "carbon_reduced_kg": 0.5,
            "total_eco_score": 95,
            "achievements": ["energy_saver", "carbon_champion"]
        }

        manager.add_eco_credits(eco_data)

        # Retrieve and verify eco credits
        retrieved = manager.get_session_eco_credits("eco_test_session")
        assert retrieved is not None
        assert abs(retrieved["energy_saved_kwh"] - 1.25) < 0.01
        assert retrieved["water_saved_ml"] == 180.0
        assert retrieved["total_eco_score"] == 95
        assert set(retrieved["achievements"]) == {"energy_saver", "carbon_champion"}

    def test_performance_analytics_table(self, temp_db):
        """Test performance metrics table structure and operations"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Verify performance_analytics table
        result = conn.execute("PRAGMA table_info(performance_analytics)").fetchall()
        column_names = [row[1] for row in result]

        expected_columns = [
            "timestamp", "session_id", "response_time_ms", "system_load",
            "memory_usage", "model_efficiency_score", "optimization_suggestions"
        ]

        for column in expected_columns:
            assert column in column_names, f"Missing performance_analytics column: {column}"

        # Test adding performance data
        perf_data = {
            "timestamp": datetime.now(),
            "session_id": "perf_test_session",
            "response_time_ms": 1150,
            "system_load": 45.5,
            "memory_usage": 68.2,
            "model_efficiency_score": 8.7,
            "optimization_suggestions": ["reduce_context_length", "enable_caching"]
        }

        manager.add_performance_metrics(perf_data)

        # Retrieve and verify performance data
        retrieved = manager.get_session_performance_metrics("perf_test_session")
        assert len(retrieved) > 0
        latest = retrieved[0]
        assert latest["response_time_ms"] == 1150
        assert abs(latest["system_load"] - 45.5) < 0.1
        assert set(latest["optimization_suggestions"]) == {"reduce_context_length", "enable_caching"}

    def test_humor_effectiveness_queries(self, temp_db):
        """Test complex analytics queries for humor effectiveness"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.database.conversation_manager import ConversationRecord

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Add test conversations with varying humor scores
        test_conversations = [
            ("joke_001", "Tell me a joke", "Why did the AI cross the road? To get to the other site!", 8.5, ["wordplay"]),
            ("joke_002", "Another joke please", "I'm like a local coffee shop - everything happens here!", 7.0, ["analogy"]),
            ("fact_001", "Random fact", "Did you know honey never spoils?", 6.0, ["trivia"]),
            ("help_001", "How do I code?", "I can help with programming questions.", 3.0, [])
        ]

        for conv_id, user_input, ai_response, humor_score, joke_types in test_conversations:
            conversation = ConversationRecord(
                id=conv_id,
                session_id="humor_analysis_session",
                timestamp=datetime.now(),
                user_input=user_input,
                ai_response=ai_response,
                response_time_ms=1000,
                tokens_used=30,
                personality_type="witty_bartender"
            )

            humor_data = {
                "humor_score": humor_score,
                "joke_types": joke_types,
                "user_engagement_score": humor_score * 0.8,  # Simulate correlation
                "personality_consistency": 9.0,
                "eco_credits_earned": 10
            }

            manager.add_conversation_with_analytics(conversation, humor_data)

        # Test humor effectiveness analytics
        humor_stats = manager.get_humor_effectiveness_stats("humor_analysis_session")
        assert humor_stats is not None
        assert humor_stats["avg_humor_score"] > 5.0  # Should average above 5
        assert humor_stats["total_conversations"] == 4
        assert "wordplay" in humor_stats["top_joke_types"]

        # Test humor trend analysis
        trends = manager.get_humor_trends(days=1)
        assert len(trends) > 0
        assert trends[0]["session_id"] == "humor_analysis_session"

    def test_user_engagement_correlation_analysis(self, temp_db):
        """Test correlation analysis between humor and user engagement"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.database.conversation_manager import ConversationRecord

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Add conversations with known humor-engagement correlations
        correlation_test_data = [
            (8.0, 8.5),  # High humor, high engagement
            (9.0, 9.2),  # High humor, high engagement
            (3.0, 2.8),  # Low humor, low engagement
            (7.5, 7.0),  # Good humor, good engagement
            (2.0, 1.5),  # Poor humor, poor engagement
        ]

        for i, (humor_score, engagement_score) in enumerate(correlation_test_data):
            conversation = ConversationRecord(
                id=f"correlation_{i}",
                session_id="correlation_session",
                timestamp=datetime.now(),
                user_input=f"Test input {i}",
                ai_response=f"Test response {i}",
                response_time_ms=1000,
                tokens_used=25,
                personality_type="witty_bartender"
            )

            humor_data = {
                "humor_score": humor_score,
                "joke_types": ["test"],
                "user_engagement_score": engagement_score,
                "personality_consistency": 9.0,
                "eco_credits_earned": 10
            }

            manager.add_conversation_with_analytics(conversation, humor_data)

        # Analyze humor-engagement correlation
        correlation = manager.analyze_humor_engagement_correlation("correlation_session")
        assert correlation is not None
        assert correlation["correlation_coefficient"] > 0.8  # Should show strong positive correlation
        assert correlation["sample_size"] == 5

    def test_eco_credits_accumulation_and_achievements(self, temp_db, eco_credits_test_data):
        """Test eco-credits accumulation and achievement system"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        session_data = eco_credits_test_data["session_data"]
        expected_credits = eco_credits_test_data["expected_credits"]

        # Calculate eco credits for session
        calculated_credits = manager.calculate_session_eco_credits(
            duration_minutes=session_data["duration_minutes"],
            responses_generated=session_data["responses_generated"],
            tokens_processed=session_data["tokens_processed"]
        )

        # Verify calculations match expected values (within 5% tolerance)
        for metric, expected_value in expected_credits.items():
            calculated_value = calculated_credits[metric]
            tolerance = expected_value * 0.05  # 5% tolerance
            assert abs(calculated_value - expected_value) <= tolerance, \
                f"Eco credit calculation for {metric}: expected {expected_value}, got {calculated_value}"

        # Test achievement unlocking
        achievements = manager.check_eco_achievements(calculated_credits)
        expected_achievements = ["energy_saver", "water_guardian"]  # Based on test data

        for achievement in expected_achievements:
            assert achievement in achievements, f"Should unlock achievement: {achievement}"

    def test_personality_consistency_tracking(self, temp_db, personality_consistency_tests):
        """Test tracking personality consistency over time"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager
        from src.database.conversation_manager import ConversationRecord

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        session_id = "consistency_tracking_session"

        # Add conversations with varying consistency scores
        for i, test_case in enumerate(personality_consistency_tests):
            conversation = ConversationRecord(
                id=f"consistency_{i}",
                session_id=session_id,
                timestamp=datetime.now() + timedelta(minutes=i),
                user_input=f"Test input {i}",
                ai_response=test_case["response"],
                response_time_ms=1000,
                tokens_used=30,
                personality_type="witty_bartender"
            )

            humor_data = {
                "humor_score": 7.0,
                "joke_types": ["test"],
                "user_engagement_score": 7.0,
                "personality_consistency": test_case["expected_consistency"],
                "eco_credits_earned": 10
            }

            manager.add_conversation_with_analytics(conversation, humor_data)

        # Analyze personality consistency trends
        consistency_trend = manager.get_personality_consistency_trend(session_id)
        assert len(consistency_trend) == len(personality_consistency_tests)

        # Should identify consistency issues
        avg_consistency = sum(point["consistency_score"] for point in consistency_trend) / len(consistency_trend)

        # Calculate expected average
        expected_avg = sum(test["expected_consistency"] for test in personality_consistency_tests) / len(personality_consistency_tests)
        assert abs(avg_consistency - expected_avg) < 0.5

    def test_performance_optimization_suggestions(self, temp_db):
        """Test automatic performance optimization suggestions"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Add performance data that should trigger optimization suggestions
        slow_performance_data = {
            "timestamp": datetime.now(),
            "session_id": "slow_session",
            "response_time_ms": 3500,  # Slow response
            "system_load": 85.0,       # High load
            "memory_usage": 90.0,      # High memory
            "model_efficiency_score": 4.0,  # Low efficiency
            "optimization_suggestions": []
        }

        # System should generate optimization suggestions
        suggestions = manager.generate_optimization_suggestions(slow_performance_data)

        expected_suggestions = [
            "reduce_context_length",    # For slow response
            "optimize_model_selection", # For low efficiency
            "enable_response_caching",  # For performance
            "monitor_system_resources"  # For high load
        ]

        for suggestion in expected_suggestions:
            assert suggestion in suggestions, f"Should suggest: {suggestion}"

        # Store with suggestions
        slow_performance_data["optimization_suggestions"] = suggestions
        manager.add_performance_metrics(slow_performance_data)

        # Verify suggestions were stored
        retrieved = manager.get_session_performance_metrics("slow_session")
        assert len(retrieved) > 0
        assert len(retrieved[0]["optimization_suggestions"]) > 0

class TestAnalyticsQueries:
    """Test suite for advanced analytics queries"""

    def test_humor_effectiveness_by_time_of_day(self, temp_db):
        """Test analyzing humor effectiveness by time of day"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # This test validates the query structure for time-based analysis
        # Implementation will be added to make this pass
        time_analysis = manager.get_humor_effectiveness_by_time_of_day(days=7)

        # Should return data grouped by hour
        assert isinstance(time_analysis, list)
        if time_analysis:  # If we have data
            assert "hour" in time_analysis[0]
            assert "avg_humor_score" in time_analysis[0]
            assert "engagement_score" in time_analysis[0]

    def test_eco_credits_leaderboard(self, temp_db):
        """Test eco-credits leaderboard functionality"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Test leaderboard query structure
        leaderboard = manager.get_eco_credits_leaderboard(limit=10)

        assert isinstance(leaderboard, list)
        if leaderboard:  # If we have data
            assert "session_id" in leaderboard[0]
            assert "total_eco_score" in leaderboard[0]
            assert "energy_saved_kwh" in leaderboard[0]

    def test_performance_benchmark_comparison(self, temp_db):
        """Test performance benchmarking against historical data"""
        from src.database.enhanced_conversation_manager import EnhancedConversationManager

        conn, db_path = temp_db
        manager = EnhancedConversationManager(db_path=db_path)

        # Test benchmark comparison
        current_metrics = {
            "response_time_ms": 1200,
            "system_load": 45.0,
            "memory_usage": 60.0
        }

        benchmark = manager.compare_performance_to_benchmark(current_metrics, days=30)

        assert isinstance(benchmark, dict)
        assert "performance_percentile" in benchmark
        assert "historical_average" in benchmark
        assert "improvement_suggestions" in benchmark

if __name__ == "__main__":
    pytest.main([__file__, "-v"])