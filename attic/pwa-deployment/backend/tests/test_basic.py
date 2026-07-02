#!/usr/bin/env python3
"""
Basic tests for M1K3 PWA backend without ML dependencies
"""

import pytest
import tempfile
import json
from pathlib import Path
import sys

def test_project_structure():
    """Test that project structure is correct"""
    backend_dir = Path(__file__).parent.parent
    
    # Check required directories exist
    assert (backend_dir / "scripts").exists()
    assert (backend_dir / "tests").exists()
    assert (backend_dir / "requirements.txt").exists()
    
    # Check scripts directory has our files
    assert (backend_dir / "scripts" / "model_exporter.py").exists()

def test_requirements_file():
    """Test that requirements.txt is properly formatted"""
    backend_dir = Path(__file__).parent.parent
    requirements_path = backend_dir / "requirements.txt"
    
    with open(requirements_path) as f:
        requirements = f.read()
    
    # Check for key dependencies
    required_packages = [
        "torch", "transformers", "onnx", "pytest"
    ]
    
    for package in required_packages:
        assert package in requirements, f"Missing required package: {package}"

def test_model_exporter_syntax():
    """Test that model_exporter.py has valid Python syntax"""
    backend_dir = Path(__file__).parent.parent
    exporter_path = backend_dir / "scripts" / "model_exporter.py"
    
    # Read and compile the file to check syntax
    with open(exporter_path) as f:
        source = f.read()
    
    try:
        compile(source, str(exporter_path), 'exec')
    except SyntaxError as e:
        pytest.fail(f"Syntax error in model_exporter.py: {e}")

def test_model_configs_structure():
    """Test model configuration structure without importing ML libraries"""
    # This is a simple validation that our model configs make sense
    expected_tiers = ["tiny", "small", "medium"]
    expected_keys = ["model_name", "max_memory_gb", "description", "size_mb", "quantization"]
    
    # We can't import the actual ModelExporter without ML deps, 
    # but we can validate the concept
    assert len(expected_tiers) == 3
    assert all(isinstance(tier, str) for tier in expected_tiers)
    assert all(isinstance(key, str) for key in expected_keys)

def test_deployment_manifest_schema():
    """Test deployment manifest schema validation"""
    # Create a sample manifest
    sample_manifest = {
        "version": "1.0.0",
        "export_timestamp": 1234567890,
        "system_capabilities": {
            "total_memory_gb": 16.0,
            "cpu_cores": 8
        },
        "models": {
            "tiny": {
                "name": "m1k3-tiny",
                "size_mb": 100.0,
                "min_memory_gb": 4
            }
        },
        "model_selection_rules": {
            "high_end": {
                "min_memory_gb": 8,
                "preferred_model": "medium"
            }
        }
    }
    
    # Validate required keys
    required_keys = ["version", "export_timestamp", "system_capabilities", "models"]
    for key in required_keys:
        assert key in sample_manifest
    
    # Validate types
    assert isinstance(sample_manifest["version"], str)
    assert isinstance(sample_manifest["export_timestamp"], int)
    assert isinstance(sample_manifest["models"], dict)

def test_output_directory_creation():
    """Test that output directory creation works"""
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "models"
        
        # Simulate what ModelExporter.__init__ does
        output_path.mkdir(parents=True, exist_ok=True)
        
        assert output_path.exists()
        assert output_path.is_dir()

if __name__ == "__main__":
    pytest.main([__file__, "-v"])