#!/usr/bin/env python3
"""
Audio Test Framework for TTS Testing
Provides interactive audio testing capabilities with live playback and feedback
"""

import os
import sys
import time
import threading
from typing import Dict, Any, Optional, Callable, List
from enum import Enum
from dataclasses import dataclass

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from voice_engine import create_voice_engine
    VOICE_ENGINE_AVAILABLE = True
except ImportError:
    VOICE_ENGINE_AVAILABLE = False

class TestResult(Enum):
    """Test result options for user feedback"""
    PASS = "✅ PASS"
    FAIL = "❌ FAIL" 
    NEEDS_IMPROVEMENT = "🔧 NEEDS IMPROVEMENT"
    SKIP = "⏭️ SKIP"

@dataclass
class AudioTestCase:
    """Represents an audio test case with expected behavior"""
    name: str
    description: str
    test_text: str
    expected_voice_characteristics: Dict[str, Any]
    setup_function: Optional[Callable] = None
    cleanup_function: Optional[Callable] = None

class AudioTestFramework:
    """Interactive audio testing framework for TTS development"""
    
    def __init__(self, voice_engine=None, interactive: bool = True):
        self.interactive = interactive
        self.voice_engine = voice_engine or self._create_voice_engine()
        self.test_results: Dict[str, TestResult] = {}
        self.test_notes: Dict[str, str] = {}
        
    def _create_voice_engine(self):
        """Create voice engine for testing"""
        if VOICE_ENGINE_AVAILABLE:
            engine = create_voice_engine()
            if engine.load_model():
                return engine
        
        print("⚠️  Voice engine not available - audio tests will be simulated")
        return None
    
    def play_and_wait_for_feedback(self, audio_text: str, description: str, 
                                 expected_characteristics: Dict[str, Any] = None) -> TestResult:
        """
        Play audio and prompt for user feedback
        
        Args:
            audio_text: Text to synthesize and play
            description: Description of what we're testing
            expected_characteristics: Expected voice characteristics
        
        Returns:
            TestResult based on user feedback
        """
        print(f"\n🔊 Audio Test: {description}")
        print(f"📝 Text: '{audio_text}'")
        
        if expected_characteristics:
            print("🎯 Expected characteristics:")
            for key, value in expected_characteristics.items():
                print(f"   • {key}: {value}")
        
        if not self.voice_engine:
            print("🤖 [SIMULATED] Audio would play here")
            if not self.interactive:
                return TestResult.PASS
        else:
            print("▶️  Playing audio... (Listen carefully!)")
            try:
                self.voice_engine.synthesize_and_play(audio_text, background=False)
            except Exception as e:
                print(f"❌ Audio playback failed: {e}")
                return TestResult.FAIL
        
        if not self.interactive:
            return TestResult.PASS
            
        # Get user feedback
        return self._get_user_feedback(description)
    
    def compare_audio_samples(self, original_text: str, enhanced_text: str, 
                            enhancement_description: str) -> TestResult:
        """
        Play two audio samples for comparison
        
        Args:
            original_text: Original text/settings
            enhanced_text: Enhanced text/settings  
            enhancement_description: What enhancement was made
        
        Returns:
            TestResult based on comparison
        """
        print(f"\n🔀 Audio Comparison: {enhancement_description}")
        print("🅰️  Playing ORIGINAL version...")
        
        if self.voice_engine:
            try:
                self.voice_engine.synthesize_and_play(original_text, background=False)
            except Exception as e:
                print(f"❌ Original audio failed: {e}")
        else:
            print("🤖 [SIMULATED] Original audio would play here")
        
        time.sleep(1)  # Brief pause between samples
        
        print("🅱️  Playing ENHANCED version...")
        if self.voice_engine:
            try:
                self.voice_engine.synthesize_and_play(enhanced_text, background=False)
            except Exception as e:
                print(f"❌ Enhanced audio failed: {e}")
        else:
            print("🤖 [SIMULATED] Enhanced audio would play here")
        
        if not self.interactive:
            return TestResult.PASS
            
        return self._get_comparison_feedback()
    
    def interactive_parameter_tuning(self, text: str, effect_name: str, 
                                   parameters: Dict[str, Any], 
                                   parameter_ranges: Dict[str, tuple]) -> Dict[str, Any]:
        """
        Live adjustment of parameters with immediate playback
        
        Args:
            text: Text to use for testing
            effect_name: Name of the effect being tuned
            parameters: Current parameter values
            parameter_ranges: (min, max) for each parameter
        
        Returns:
            Optimized parameter values
        """
        print(f"\n🎛️  Interactive Parameter Tuning: {effect_name}")
        print(f"📝 Test text: '{text}'")
        
        if not self.voice_engine:
            print("🤖 [SIMULATED] Parameter tuning would happen here")
            return parameters
        
        current_params = parameters.copy()
        
        while True:
            # Display current parameters
            print("\n📊 Current parameters:")
            for param, value in current_params.items():
                print(f"   • {param}: {value}")
            
            # Play with current settings
            print("▶️  Playing with current settings...")
            try:
                # Apply parameters to voice engine (implementation depends on engine)
                self._apply_parameters_to_engine(effect_name, current_params)
                self.voice_engine.synthesize_and_play(text, background=False)
            except Exception as e:
                print(f"❌ Playback failed: {e}")
            
            # Get user choice
            print("\n🎛️  Parameter tuning options:")
            print("   1. Adjust parameter")
            print("   2. Test again")
            print("   3. Save and continue")
            print("   4. Reset to defaults")
            
            choice = input("Choose option (1-4): ").strip()
            
            if choice == "1":
                current_params = self._adjust_parameter_interactive(current_params, parameter_ranges)
            elif choice == "2":
                continue  # Will loop back to play again
            elif choice == "3":
                break  # Exit with current parameters
            elif choice == "4":
                current_params = parameters.copy()  # Reset to original
            else:
                print("Invalid choice, please try again.")
        
        return current_params
    
    def run_test_suite(self, test_cases: List[AudioTestCase]) -> Dict[str, TestResult]:
        """
        Run a complete test suite with audio feedback
        
        Args:
            test_cases: List of test cases to run
        
        Returns:
            Dictionary mapping test names to results
        """
        print(f"\n🧪 Running Audio Test Suite ({len(test_cases)} tests)")
        print("=" * 50)
        
        results = {}
        
        for i, test_case in enumerate(test_cases, 1):
            print(f"\n[{i}/{len(test_cases)}] {test_case.name}")
            
            # Run setup if provided
            if test_case.setup_function:
                try:
                    test_case.setup_function()
                except Exception as e:
                    print(f"⚠️  Setup failed: {e}")
            
            # Run the actual test
            try:
                result = self.play_and_wait_for_feedback(
                    test_case.test_text,
                    test_case.description,
                    test_case.expected_voice_characteristics
                )
                results[test_case.name] = result
                
                # Store any user notes
                if self.interactive and result != TestResult.PASS:
                    note = input("📝 Any notes about this test? (or press Enter to skip): ").strip()
                    if note:
                        self.test_notes[test_case.name] = note
                        
            except Exception as e:
                print(f"❌ Test failed with exception: {e}")
                results[test_case.name] = TestResult.FAIL
            
            # Run cleanup if provided
            if test_case.cleanup_function:
                try:
                    test_case.cleanup_function()
                except Exception as e:
                    print(f"⚠️  Cleanup failed: {e}")
        
        # Display summary
        self._display_test_summary(results)
        return results
    
    def _get_user_feedback(self, test_description: str) -> TestResult:
        """Get user feedback about audio quality"""
        print(f"\n🎧 How did the audio sound for '{test_description}'?")
        print("   1. ✅ Perfect - exactly as expected")
        print("   2. 🔧 Good but could be improved")
        print("   3. ❌ Not good - doesn't match expectations")
        print("   4. ⏭️  Skip this test")
        
        try:
            while True:
                choice = input("Your assessment (1-4): ").strip()
                
                if choice == "1":
                    return TestResult.PASS
                elif choice == "2":
                    return TestResult.NEEDS_IMPROVEMENT
                elif choice == "3":
                    return TestResult.FAIL
                elif choice == "4":
                    return TestResult.SKIP
                else:
                    print("Please enter 1, 2, 3, or 4")
        except (EOFError, KeyboardInterrupt):
            print("\n⏭️  Skipping due to input issues")
            return TestResult.SKIP
    
    def _get_comparison_feedback(self) -> TestResult:
        """Get user feedback about audio comparison"""
        print(f"\n🎧 Which version sounded better?")
        print("   1. ✅ Enhanced version is clearly better")
        print("   2. 🔧 Enhanced version is slightly better")
        print("   3. 🤷 No significant difference")
        print("   4. ❌ Original version was better")
        print("   5. ⏭️  Skip this comparison")
        
        while True:
            choice = input("Your assessment (1-5): ").strip()
            
            if choice == "1":
                return TestResult.PASS
            elif choice == "2":
                return TestResult.NEEDS_IMPROVEMENT
            elif choice in ["3", "4"]:
                return TestResult.FAIL
            elif choice == "5":
                return TestResult.SKIP
            else:
                print("Please enter 1, 2, 3, 4, or 5")
    
    def _adjust_parameter_interactive(self, current_params: Dict[str, Any], 
                                    ranges: Dict[str, tuple]) -> Dict[str, Any]:
        """Interactive parameter adjustment"""
        print("\n🎛️  Which parameter would you like to adjust?")
        
        param_list = list(current_params.keys())
        for i, param in enumerate(param_list, 1):
            min_val, max_val = ranges.get(param, (0, 1))
            print(f"   {i}. {param}: {current_params[param]} (range: {min_val}-{max_val})")
        
        while True:
            try:
                choice = int(input(f"Choose parameter (1-{len(param_list)}): "))
                if 1 <= choice <= len(param_list):
                    break
                else:
                    print(f"Please enter a number between 1 and {len(param_list)}")
            except ValueError:
                print("Please enter a valid number")
        
        param_name = param_list[choice - 1]
        min_val, max_val = ranges.get(param_name, (0, 1))
        
        while True:
            try:
                new_value = float(input(f"New value for {param_name} ({min_val}-{max_val}): "))
                if min_val <= new_value <= max_val:
                    current_params[param_name] = new_value
                    break
                else:
                    print(f"Value must be between {min_val} and {max_val}")
            except ValueError:
                print("Please enter a valid number")
        
        return current_params
    
    def _apply_parameters_to_engine(self, effect_name: str, parameters: Dict[str, Any]):
        """Apply parameters to the voice engine (stub for now)"""
        # This will be implemented when we have the actual effects system
        print(f"🎛️  Applied {effect_name} parameters: {parameters}")
    
    def _display_test_summary(self, results: Dict[str, TestResult]):
        """Display test summary with statistics"""
        print("\n📊 Audio Test Summary")
        print("=" * 30)
        
        total_tests = len(results)
        passed = sum(1 for r in results.values() if r == TestResult.PASS)
        needs_improvement = sum(1 for r in results.values() if r == TestResult.NEEDS_IMPROVEMENT)
        failed = sum(1 for r in results.values() if r == TestResult.FAIL)
        skipped = sum(1 for r in results.values() if r == TestResult.SKIP)
        
        print(f"Total tests: {total_tests}")
        print(f"✅ Passed: {passed}")
        print(f"🔧 Needs improvement: {needs_improvement}")
        print(f"❌ Failed: {failed}")
        print(f"⏭️  Skipped: {skipped}")
        
        if total_tests > 0:
            success_rate = (passed + needs_improvement) / total_tests * 100
            print(f"🎯 Success rate: {success_rate:.1f}%")
        
        # Show detailed results
        print(f"\nDetailed results:")
        for test_name, result in results.items():
            note = self.test_notes.get(test_name, "")
            note_text = f" - {note}" if note else ""
            print(f"  {result.value} {test_name}{note_text}")

def create_audio_test_framework(interactive: bool = True) -> AudioTestFramework:
    """Factory function to create audio test framework"""
    return AudioTestFramework(interactive=interactive)

if __name__ == "__main__":
    # Quick test of the framework
    framework = create_audio_test_framework()
    
    # Test basic functionality
    result = framework.play_and_wait_for_feedback(
        "This is a test of the audio framework",
        "Basic audio playback test",
        {"clarity": "high", "pace": "normal"}
    )
    
    print(f"Test result: {result}")