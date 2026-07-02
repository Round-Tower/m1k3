import time
import pytest
import sys
from pathlib import Path

# Add src directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from cli.cli_core import M1K3CLICore

# Define a reasonable startup time threshold in seconds
# The current time is ~6-8s. A good target after refactor is < 1s.
STARTUP_THRESHOLD = 1.0 # seconds

def test_cli_initialization_speed():
    """
    Tests that the CLI core initializes faster than the threshold
    due to lazy loading.
    """
    start_time = time.time()
    
    # __init__ is fast, the real work is in initialize()
    cli_core = M1K3CLICore(
        voice_enabled=False,
        auto_avatar=False,
        rag_enabled=False,
        stt_engine='none'
    )
    
    # This is the slow part we need to measure and refactor
    initialized_successfully = cli_core.initialize()
    
    end_time = time.time()
    
    duration = end_time - start_time
    
    print(f"CLI initialization took: {duration:.4f} seconds")
    
    assert initialized_successfully, "CLI core failed to initialize."
    assert duration < STARTUP_THRESHOLD, f"CLI startup is too slow! Took {duration:.4f}s, expected less than {STARTUP_THRESHOLD}s."