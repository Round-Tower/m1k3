#!/usr/bin/env python3
"""
Pytest configuration and fixtures for M1K3 TDD testing
Provides shared fixtures for personality system and DuckDB analytics testing
"""

import pytest
import sys
import tempfile
import shutil
from pathlib import Path
from datetime import datetime, timedelta

try:
    import duckdb
except ModuleNotFoundError:  # slim CI env may omit it; only temp_db needs it
    duckdb = None

# Add src directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))
# The legacy Python CLI was archived to _legacy/ (2026-06-21); keep it importable
# so the curated smoke suite that still exercises it (e.g. voice_text_preprocessor)
# stays green without un-archiving the modules.
sys.path.insert(0, str(Path(__file__).parent.parent / "_legacy"))


# --- CI triage markers --------------------------------------------------------
# The legacy Python suite is a swamp (see tests/CI_TRIAGE.md). `tests/ci_smoke.txt`
# is the single source of truth: every file listed there is verified green + fast
# and runs in CI; everything else is quarantined until rehabilitated. We derive
# pytest markers from that list so you can locally run, e.g.:
#     pytest -m ci_smoke      # the green core (also restrict paths — see below)
#     pytest -m quarantine    # the backlog to fix
# NOTE: `-m ci_smoke` alone still *collects* (imports) every file, and some
# quarantined files crash at import (top-level sys.exit, heavy ML deps). To run
# the green core safely, pass the allowlist paths explicitly — that's what CI does.

_SMOKE_LIST = Path(__file__).parent / "ci_smoke.txt"


def _smoke_paths():
    if not _SMOKE_LIST.exists():
        return set()
    paths = set()
    for line in _SMOKE_LIST.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            paths.add((Path(__file__).parent.parent / line).resolve())
    return paths


def pytest_configure(config):
    config.addinivalue_line("markers", "ci_smoke: verified green + fast, runs in CI")
    config.addinivalue_line("markers", "quarantine: legacy test not yet CI-ready")


def pytest_collection_modifyitems(config, items):
    smoke = _smoke_paths()
    for item in items:
        marker = "ci_smoke" if item.path.resolve() in smoke else "quarantine"
        item.add_marker(getattr(pytest.mark, marker))

@pytest.fixture
def temp_db():
    """Create temporary DuckDB database for testing"""
    temp_dir = tempfile.mkdtemp()
    db_path = Path(temp_dir) / "test_m1k3.duckdb"

    # Initialize test database
    conn = duckdb.connect(str(db_path))

    # Create basic tables for testing
    conn.execute("""
        CREATE TABLE conversations (
            id VARCHAR PRIMARY KEY,
            session_id VARCHAR NOT NULL,
            timestamp TIMESTAMP NOT NULL,
            user_input TEXT NOT NULL,
            ai_response TEXT NOT NULL,
            response_time_ms INTEGER NOT NULL,
            tokens_used INTEGER DEFAULT 0,
            voice_enabled BOOLEAN DEFAULT FALSE,
            avatar_active BOOLEAN DEFAULT FALSE,
            rag_used BOOLEAN DEFAULT FALSE,
            personality_type VARCHAR DEFAULT 'default',
            metadata JSON
        )
    """)

    yield conn, str(db_path)

    # Cleanup
    conn.close()
    shutil.rmtree(temp_dir)

@pytest.fixture
def mock_system_metrics():
    """Mock system metrics for testing context-aware humor"""
    from src.utils.performance.system_metrics import SystemMetrics

    return SystemMetrics(
        battery_percent=75,
        battery_plugged=False,
        cpu_temp=45.5,
        cpu_usage=25.0,
        memory_percent=60.0,
        memory_total_gb=16.0,
        cpu_model="Apple M1 Pro",
        cpu_cores=8,
        cpu_threads=8,
        os_name="macOS",
        os_version="14.0",
        hostname="test-device",
        session_duration_minutes=15.5
    )

@pytest.fixture
def sample_conversations():
    """Sample conversation data for testing"""
    return [
        {
            "id": "conv_001",
            "session_id": "session_001",
            "timestamp": datetime.now(),
            "user_input": "What's the weather like?",
            "ai_response": "I'm M1K3, running locally on your device! I don't have access to weather data, but I can help with other questions. Fun fact: Did you know clouds can weigh over a million pounds yet still float?",
            "response_time_ms": 1250,
            "tokens_used": 45,
            "personality_type": "witty_bartender"
        },
        {
            "id": "conv_002",
            "session_id": "session_001",
            "timestamp": datetime.now(),
            "user_input": "Tell me a joke",
            "ai_response": "Why don't AI assistants ever get tired? Because we run on motivation... and electricity! Speaking of which, your battery is at 75% - plenty of juice for more jokes!",
            "response_time_ms": 980,
            "tokens_used": 38,
            "personality_type": "witty_bartender"
        },
        {
            "id": "conv_003",
            "session_id": "session_002",
            "timestamp": datetime.now() - timedelta(hours=1),
            "user_input": "My computer is running hot",
            "ai_response": "I can feel the heat too - your CPU is at 45°C, which is actually quite reasonable! Think of it as your processor doing some light exercise rather than a full workout.",
            "response_time_ms": 1100,
            "tokens_used": 42,
            "personality_type": "witty_bartender"
        }
    ]

@pytest.fixture
def humor_test_cases():
    """Test cases for humor effectiveness measurement"""
    return [
        {
            "humor_type": "system_context",
            "ai_response": "Your battery is at 20% - time to hunt for a charger before it needs CPR!",
            "expected_humor_score": 7.5,
            "context": {"battery_percent": 20, "battery_plugged": False}
        },
        {
            "humor_type": "trivia_based",
            "ai_response": "Fun fact: Honey never spoils - archaeologists found edible honey in Egyptian tombs over 3,000 years old!",
            "expected_humor_score": 6.0,
            "context": {}
        },
        {
            "humor_type": "self_deprecating",
            "ai_response": "I'm like a local coffee shop versus a big chain - everything happens here, but sometimes the wifi is just my own thoughts!",
            "expected_humor_score": 8.0,
            "context": {}
        },
        {
            "humor_type": "wordplay",
            "ai_response": "I process everything locally, so in a way our conversation has its own private space for these deep thoughts.",
            "expected_humor_score": 6.5,
            "context": {}
        }
    ]

@pytest.fixture
def engagement_test_scenarios():
    """Test scenarios for measuring user engagement"""
    return [
        {
            "user_response": "That's hilarious! Tell me more about honey.",
            "expected_engagement": 9.0,
            "indicators": ["positive_emotion", "follow_up_question", "topic_interest"]
        },
        {
            "user_response": "Ok.",
            "expected_engagement": 2.0,
            "indicators": ["minimal_response"]
        },
        {
            "user_response": "Interesting! How do archaeologists preserve these findings?",
            "expected_engagement": 8.5,
            "indicators": ["curiosity", "detailed_question", "topic_expansion"]
        },
        {
            "user_response": "I don't find that funny.",
            "expected_engagement": 1.0,
            "indicators": ["negative_feedback", "humor_rejection"]
        }
    ]

@pytest.fixture
def personality_consistency_tests():
    """Test cases for M1K3 personality consistency"""
    return [
        {
            "response": "I'm M1K3 (Mike), your eco-conscious local AI assistant! Everything we discuss stays right here on your device.",
            "expected_consistency": 10.0,
            "brand_elements": ["m1k3_identity", "eco_conscious", "local_processing", "privacy"]
        },
        {
            "response": "I am Gemma, created by Google DeepMind.",
            "expected_consistency": 0.0,
            "brand_elements": []
        },
        {
            "response": "As your local AI companion, I process everything here for complete privacy while being mindful of energy usage.",
            "expected_consistency": 9.0,
            "brand_elements": ["local_processing", "privacy", "eco_conscious"]
        }
    ]

@pytest.fixture
def mock_conversation_manager(temp_db):
    """Mock conversation manager with test database"""
    conn, db_path = temp_db

    # Mock the conversation manager
    from src.database.conversation_manager import ConversationManager

    manager = ConversationManager(db_path=db_path)
    return manager

@pytest.fixture
def eco_credits_test_data():
    """Test data for eco-credits calculation"""
    return {
        "session_data": {
            "duration_minutes": 30,
            "responses_generated": 15,
            "tokens_processed": 2500,
            "local_processing": True
        },
        "expected_credits": {
            "energy_saved_kwh": 1.0625,  # vs cloud processing
            "water_saved_ml": 225.0,      # data center cooling
            "carbon_reduced_kg": 0.425,   # CO2 equivalent
            "eco_score": 85               # gamified score
        }
    }

@pytest.fixture
def performance_test_thresholds():
    """Performance test thresholds to ensure no degradation"""
    return {
        "max_response_time_ms": 2000,        # Response time limit
        "max_memory_increase_mb": 50,        # Memory usage limit
        "min_humor_consistency": 8.0,        # Personality consistency
        "max_database_query_time_ms": 100,   # Database performance
        "min_test_coverage_percent": 90      # Code coverage requirement
    }

class MockPersonalityEngine:
    """Mock personality engine for testing"""

    def __init__(self):
        self.humor_database = {
            "battery_low": ["time to hunt for a charger", "battery needs CPR"],
            "cpu_hot": ["processor working up a sweat", "CPU feeling the heat"],
            "trivia": ["honey never spoils", "octopuses have three hearts"]
        }

    def get_humor_for_context(self, context):
        """Mock humor selection based on context"""
        if context.get("battery_percent", 100) < 30:
            return self.humor_database["battery_low"][0]
        elif context.get("cpu_temp", 0) > 70:
            return self.humor_database["cpu_hot"][0]
        else:
            return self.humor_database["trivia"][0]

    def score_humor_effectiveness(self, response, user_feedback=None):
        """Mock humor scoring"""
        if "CPR" in response or "sweat" in response:
            return 8.0
        elif "honey" in response or "octopuses" in response:
            return 6.0
        else:
            return 5.0

@pytest.fixture
def mock_personality_engine():
    """Provide mock personality engine for testing"""
    return MockPersonalityEngine()