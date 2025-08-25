#!/usr/bin/env python3
"""
Import fixer for M1K3 refactoring
Systematically updates imports to match new directory structure
"""

import os
import re
from pathlib import Path

# Mapping of old imports to new imports
IMPORT_MAPPINGS = {
    # AI Engines
    'from ai_inference': 'from src.engines.ai.ai_inference',
    'from simple_ai_engine': 'from src.engines.ai.simple_ai_engine',
    'from smollm_engine': 'from src.engines.ai.smollm_engine',
    'from adaptive_ai_engine': 'from src.engines.ai.adaptive_ai_engine',
    'from local_model_manager': 'from src.engines.ai.local_model_manager',
    'from model_transparency': 'from src.engines.ai.model_transparency',
    'import ai_inference': 'import src.engines.ai.ai_inference',
    
    # Voice Engines  
    'from voice_engine': 'from src.engines.voice.voice_engine',
    'from simple_voice_engine': 'from src.engines.voice.simple_voice_engine',
    'from unified_voice_engine': 'from src.engines.voice.unified_voice_engine',
    'from turbo_voice_engine': 'from src.engines.voice.turbo_voice_engine',
    'from multi_tier_voice_engine': 'from src.engines.voice.multi_tier_voice_engine',
    'from hybrid_voice_engine': 'from src.engines.voice.hybrid_voice_engine',
    'from retro_voice_engine': 'from src.engines.voice.retro_voice_engine',
    
    # CLI Components
    'from cli_animations': 'from src.cli.cli_animations',
    'from cli_model_commands': 'from src.cli.cli_model_commands',
    'from m1k3_tui': 'from src.cli.m1k3_tui',
    'from m1k3_rich_tui': 'from src.cli.m1k3_rich_tui',
    'from quick_start_cli': 'from src.cli.quick_start_cli',
    
    # Avatar System
    'from avatar_server': 'from src.avatar.avatar_server',
    'from avatar_controller': 'from src.avatar.avatar_controller',
    
    # RAG System
    'from m1k3_rag_engine': 'from src.rag.m1k3_rag_engine',
    'from m1k3_rag_integration': 'from src.rag.m1k3_rag_integration',
    
    # TTS System
    'from intelligent_tts_controller': 'from src.tts.controllers.intelligent_tts_controller',
    'from coqui_tts_manager': 'from src.tts.controllers.coqui_tts_manager',
    'from kittentts_manager': 'from src.tts.controllers.kittentts_manager',
    'from audio_effects': 'from src.tts.effects.audio_effects',
    'from content_specific_effects': 'from src.tts.effects.content_specific_effects',
    'from audio_completion_engine': 'from src.tts.effects.audio_completion_engine',
    
    # Model Management
    'from model_upgrade': 'from src.models.managers.model_upgrade',
    'from model_hot_reload_manager': 'from src.models.managers.model_hot_reload_manager',
    'from model_template_manager': 'from src.models.managers.model_template_manager',
    'from dynamic_model_monitor': 'from src.models.managers.dynamic_model_monitor',
    'from async_model_loader': 'from src.models.managers.async_model_loader',
    'from fast_startup_manager': 'from src.models.loaders.fast_startup_manager',
    'from adaptive_prompt_formatter': 'from src.models.loaders.adaptive_prompt_formatter',
    'from download_model': 'from src.models.loaders.download_model',
    'from download_models': 'from src.models.loaders.download_models',
    
    # Utilities
    'from text_processors': 'from src.utils.text_processors',
    'from response_formatter': 'from src.utils.response_formatter',
    'from model_output_parser': 'from src.utils.model_output_parser',
    'from context_aware_classification': 'from src.utils.context_aware_classification',
    'from intent_classification_system': 'from src.utils.intent_classification_system',
    'from thinking_mode_engine': 'from src.utils.thinking_mode_engine',
    'from thinking_parser': 'from src.utils.thinking_parser',
    'from thinking_quality_assurance': 'from src.utils.thinking_quality_assurance',
    'from performance_monitor': 'from src.utils.performance.performance_monitor',
    'from system_metrics': 'from src.utils.performance.system_metrics',
    'from hardware_insights': 'from src.utils.performance.hardware_insights',
    'from benchmark': 'from src.utils.performance.benchmark',
    'from performance_showcase': 'from src.utils.performance.performance_showcase',
    'from startup_profiler': 'from src.utils.performance.startup_profiler',
    'from prompt_logger': 'from src.utils.logging.prompt_logger',
    'from session_stats': 'from src.utils.logging.session_stats',
}

def fix_imports_in_file(file_path):
    """Fix imports in a single file"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_content = content
        changes_made = 0
        
        # Apply import mappings
        for old_import, new_import in IMPORT_MAPPINGS.items():
            if old_import in content:
                content = content.replace(old_import, new_import)
                changes_made += 1
        
        # Write back if changes were made
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✅ Fixed {changes_made} imports in {file_path}")
            return True
        
        return False
        
    except Exception as e:
        print(f"❌ Error fixing imports in {file_path}: {e}")
        return False

def main():
    """Fix imports across the entire codebase"""
    project_root = Path(__file__).parent
    fixed_files = 0
    total_files = 0
    
    # Find all Python files
    python_files = []
    
    # Root Python files
    for file_path in project_root.glob("*.py"):
        if file_path.name != "fix_imports.py":  # Skip this script
            python_files.append(file_path)
    
    # Source files
    for file_path in project_root.glob("src/**/*.py"):
        python_files.append(file_path)
    
    # Test files
    for file_path in project_root.glob("tests/**/*.py"):
        python_files.append(file_path)
    
    # Demo files
    for file_path in project_root.glob("demos/**/*.py"):
        python_files.append(file_path)
    
    print(f"🔍 Found {len(python_files)} Python files to process")
    
    for file_path in python_files:
        total_files += 1
        if fix_imports_in_file(file_path):
            fixed_files += 1
    
    print(f"\n📊 Import Fix Summary:")
    print(f"   Total files processed: {total_files}")
    print(f"   Files with fixes: {fixed_files}")
    print(f"   Files unchanged: {total_files - fixed_files}")

if __name__ == "__main__":
    main()