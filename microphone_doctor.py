#!/usr/bin/env python3
"""
M1K3 Microphone Doctor - Comprehensive Microphone Diagnostic Tool
Diagnoses and fixes common microphone detection and permission issues
"""

import sys
import time
import platform
import subprocess
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum

# Add src to path for imports
sys.path.insert(0, 'src')


class DiagnosticResult(Enum):
    """Diagnostic test results"""
    PASS = "✅"
    FAIL = "❌"
    WARNING = "⚠️"
    INFO = "ℹ️"
    SKIP = "⏭️"


@dataclass
class TestResult:
    """Individual test result"""
    name: str
    result: DiagnosticResult
    message: str
    details: Optional[str] = None
    fix_suggestion: Optional[str] = None


class MicrophoneDoctor:
    """Comprehensive microphone diagnostic and repair tool"""
    
    def __init__(self):
        self.test_results: List[TestResult] = []
        self.is_macos = platform.system() == "Darwin"
        self.detailed_output = False
        
    def run_full_diagnostic(self, detailed: bool = False) -> bool:
        """Run complete microphone diagnostic suite"""
        self.detailed_output = detailed
        self.test_results = []
        
        print("🩺 M1K3 Microphone Doctor - Starting Comprehensive Diagnostic")
        print("=" * 70)
        print()
        
        # Phase 1: System Detection
        print("🔍 Phase 1: System & Hardware Detection")
        print("-" * 40)
        self._test_system_info()
        self._test_hardware_detection()
        self._test_audio_drivers()
        print()
        
        # Phase 2: Permission Analysis
        print("🔐 Phase 2: Permission & Access Analysis")
        print("-" * 40)
        self._test_microphone_permissions()
        if self.is_macos:
            self._test_macos_speech_permissions()
            self._test_siri_integration()
        print()
        
        # Phase 3: Audio Pipeline Testing
        print("🎤 Phase 3: Audio Pipeline & Level Testing")
        print("-" * 40)
        self._test_audio_libraries()
        self._test_microphone_levels()
        self._test_audio_devices()
        print()
        
        # Phase 4: STT Engine Testing
        print("🧠 Phase 4: Speech-to-Text Engine Testing")
        print("-" * 40)
        self._test_stt_engines()
        print()
        
        # Generate diagnostic summary
        self._print_diagnostic_summary()
        
        # Provide automated fixes if possible
        self._suggest_automated_fixes()
        
        # Return overall health status
        return self._calculate_overall_health()
    
    def _test_system_info(self):
        """Test basic system information"""
        try:
            system = platform.system()
            version = platform.release()
            machine = platform.machine()
            
            self._add_result(TestResult(
                name="System Detection",
                result=DiagnosticResult.PASS,
                message=f"{system} {version} on {machine}",
                details=f"Platform: {platform.platform()}"
            ))
            
            # macOS version check for Speech framework
            if self.is_macos:
                try:
                    mac_version = platform.mac_ver()[0]
                    major, minor = map(int, mac_version.split('.')[:2])
                    
                    if (major > 10) or (major == 10 and minor >= 15):
                        self._add_result(TestResult(
                            name="macOS Speech Framework Compatibility",
                            result=DiagnosticResult.PASS,
                            message=f"macOS {mac_version} supports Speech Recognition",
                            details="Requires macOS 10.15 (Catalina) or later"
                        ))
                    else:
                        self._add_result(TestResult(
                            name="macOS Speech Framework Compatibility",
                            result=DiagnosticResult.FAIL,
                            message=f"macOS {mac_version} too old for Speech Recognition",
                            fix_suggestion="Upgrade to macOS 10.15 or later"
                        ))
                except Exception as e:
                    self._add_result(TestResult(
                        name="macOS Version Check",
                        result=DiagnosticResult.WARNING,
                        message=f"Could not determine macOS version: {e}"
                    ))
        except Exception as e:
            self._add_result(TestResult(
                name="System Detection",
                result=DiagnosticResult.FAIL,
                message=f"System detection failed: {e}"
            ))
    
    def _test_hardware_detection(self):
        """Test hardware detection"""
        try:
            if self.is_macos:
                # Use system_profiler to get audio hardware info
                result = subprocess.run([
                    'system_profiler', 'SPAudioDataType', '-json'
                ], capture_output=True, text=True, timeout=10)
                
                if result.returncode == 0:
                    self._add_result(TestResult(
                        name="Audio Hardware Detection",
                        result=DiagnosticResult.PASS,
                        message="Audio hardware information retrieved",
                        details="system_profiler SPAudioDataType successful"
                    ))
                else:
                    self._add_result(TestResult(
                        name="Audio Hardware Detection",
                        result=DiagnosticResult.WARNING,
                        message="Could not retrieve audio hardware info"
                    ))
            else:
                # For non-macOS systems, this would use different detection methods
                self._add_result(TestResult(
                    name="Audio Hardware Detection",
                    result=DiagnosticResult.SKIP,
                    message="Hardware detection not implemented for this OS"
                ))
                
        except subprocess.TimeoutExpired:
            self._add_result(TestResult(
                name="Audio Hardware Detection",
                result=DiagnosticResult.WARNING,
                message="Hardware detection timed out"
            ))
        except Exception as e:
            self._add_result(TestResult(
                name="Audio Hardware Detection",
                result=DiagnosticResult.WARNING,
                message=f"Hardware detection error: {e}"
            ))
    
    def _test_audio_drivers(self):
        """Test audio driver functionality"""
        if self.is_macos:
            try:
                # Check Core Audio framework
                result = subprocess.run([
                    'python3', '-c', 
                    'import ctypes; ctypes.CDLL("/System/Library/Frameworks/CoreAudio.framework/CoreAudio"); print("OK")'
                ], capture_output=True, text=True, timeout=5)
                
                if result.returncode == 0 and "OK" in result.stdout:
                    self._add_result(TestResult(
                        name="Core Audio Framework",
                        result=DiagnosticResult.PASS,
                        message="Core Audio framework accessible"
                    ))
                else:
                    self._add_result(TestResult(
                        name="Core Audio Framework",
                        result=DiagnosticResult.WARNING,
                        message="Core Audio framework check failed"
                    ))
            except Exception as e:
                self._add_result(TestResult(
                    name="Core Audio Framework",
                    result=DiagnosticResult.WARNING,
                    message=f"Core Audio test error: {e}"
                ))
        else:
            self._add_result(TestResult(
                name="Audio Driver Check",
                result=DiagnosticResult.SKIP,
                message="Driver check not implemented for this OS"
            ))
    
    def _test_microphone_permissions(self):
        """Test microphone access permissions"""
        if self.is_macos:
            try:
                # Check if Terminal or Python has microphone access
                result = subprocess.run([
                    'sqlite3', 
                    f'{self._get_home_directory()}/Library/Application Support/com.apple.TCC/TCC.db',
                    "SELECT service, client, auth_value FROM access WHERE service='kTCCServiceMicrophone';"
                ], capture_output=True, text=True, timeout=10)
                
                if result.returncode == 0:
                    permissions = result.stdout.strip()
                    if permissions:
                        self._add_result(TestResult(
                            name="Microphone Permissions",
                            result=DiagnosticResult.INFO,
                            message="Found microphone permission entries",
                            details=f"TCC database entries: {permissions[:100]}..."
                        ))
                    else:
                        self._add_result(TestResult(
                            name="Microphone Permissions",
                            result=DiagnosticResult.WARNING,
                            message="No microphone permission entries found",
                            fix_suggestion="May need to grant microphone access to Terminal/Python"
                        ))
                else:
                    self._add_result(TestResult(
                        name="Microphone Permissions",
                        result=DiagnosticResult.WARNING,
                        message="Could not check TCC database",
                        fix_suggestion="Check System Preferences > Security & Privacy > Privacy > Microphone"
                    ))
            except Exception as e:
                self._add_result(TestResult(
                    name="Microphone Permissions",
                    result=DiagnosticResult.WARNING,
                    message=f"Permission check error: {e}",
                    fix_suggestion="Check System Preferences > Security & Privacy > Privacy > Microphone"
                ))
        else:
            self._add_result(TestResult(
                name="Microphone Permissions",
                result=DiagnosticResult.SKIP,
                message="Permission check not implemented for this OS"
            ))
    
    def _test_macos_speech_permissions(self):
        """Test macOS Speech Recognition permissions"""
        if not self.is_macos:
            return
            
        try:
            # Import Speech framework and check authorization
            test_code = '''
import sys
try:
    from Speech import SFSpeechRecognizer
    auth_status = SFSpeechRecognizer.authorizationStatus()
    status_map = {0: "NotDetermined", 1: "Denied", 2: "Restricted", 3: "Authorized"}
    print(f"{auth_status}:{status_map.get(auth_status, 'Unknown')}")
except ImportError as e:
    print(f"ImportError:{e}")
except Exception as e:
    print(f"Error:{e}")
'''
            
            result = subprocess.run([
                'python3', '-c', test_code
            ], capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                output = result.stdout.strip()
                if "ImportError" in output:
                    self._add_result(TestResult(
                        name="Speech Framework Import",
                        result=DiagnosticResult.FAIL,
                        message="PyObjC Speech framework not available",
                        fix_suggestion="Install with: pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation"
                    ))
                elif ":" in output:
                    status_code, status_name = output.split(":", 1)
                    if status_name == "Authorized":
                        self._add_result(TestResult(
                            name="Speech Recognition Permission",
                            result=DiagnosticResult.PASS,
                            message="Speech Recognition authorized"
                        ))
                    elif status_name == "NotDetermined":
                        self._add_result(TestResult(
                            name="Speech Recognition Permission",
                            result=DiagnosticResult.WARNING,
                            message="Speech Recognition permission not determined",
                            fix_suggestion="Run M1K3 CLI to trigger permission request"
                        ))
                    else:
                        self._add_result(TestResult(
                            name="Speech Recognition Permission",
                            result=DiagnosticResult.FAIL,
                            message=f"Speech Recognition {status_name.lower()}",
                            fix_suggestion="Enable in System Preferences > Security & Privacy > Speech Recognition"
                        ))
                else:
                    self._add_result(TestResult(
                        name="Speech Recognition Permission",
                        result=DiagnosticResult.WARNING,
                        message=f"Unexpected response: {output}"
                    ))
            else:
                self._add_result(TestResult(
                    name="Speech Recognition Permission",
                    result=DiagnosticResult.FAIL,
                    message="Speech permission check failed",
                    details=result.stderr
                ))
        except Exception as e:
            self._add_result(TestResult(
                name="Speech Recognition Permission",
                result=DiagnosticResult.WARNING,
                message=f"Permission test error: {e}"
            ))
    
    def _test_siri_integration(self):
        """Test Siri integration (required for Speech Recognition)"""
        if not self.is_macos:
            return
            
        try:
            # Check if Siri is enabled (required for SFSpeechRecognizer)
            result = subprocess.run([
                'defaults', 'read', 'com.apple.assistant.support', 'Assistant Enabled'
            ], capture_output=True, text=True)
            
            if result.returncode == 0:
                enabled = result.stdout.strip()
                if enabled == "1":
                    self._add_result(TestResult(
                        name="Siri Integration",
                        result=DiagnosticResult.PASS,
                        message="Siri is enabled (required for Speech Recognition)"
                    ))
                else:
                    self._add_result(TestResult(
                        name="Siri Integration",
                        result=DiagnosticResult.FAIL,
                        message="Siri is disabled",
                        fix_suggestion="Enable Siri in System Preferences > Siri & Spotlight"
                    ))
            else:
                self._add_result(TestResult(
                    name="Siri Integration",
                    result=DiagnosticResult.WARNING,
                    message="Could not check Siri status",
                    fix_suggestion="Ensure Siri is enabled in System Preferences"
                ))
        except Exception as e:
            self._add_result(TestResult(
                name="Siri Integration",
                result=DiagnosticResult.WARNING,
                message=f"Siri check error: {e}"
            ))
    
    def _test_audio_libraries(self):
        """Test required audio libraries"""
        libraries_to_test = [
            ("sounddevice", "pip install sounddevice"),
            ("numpy", "pip install numpy"),
            ("speech_recognition", "pip install SpeechRecognition"),
        ]
        
        if self.is_macos:
            libraries_to_test.extend([
                ("objc", "pip install pyobjc"),
                ("Foundation", "pip install pyobjc-framework-Cocoa"),
                ("Speech", "pip install pyobjc-framework-Speech"),
                ("AVFoundation", "pip install pyobjc-framework-AVFoundation"),
            ])
        
        for lib_name, install_cmd in libraries_to_test:
            try:
                result = subprocess.run([
                    'python3', '-c', f'import {lib_name}; print("OK")'
                ], capture_output=True, text=True, timeout=5)
                
                if result.returncode == 0 and "OK" in result.stdout:
                    self._add_result(TestResult(
                        name=f"Library: {lib_name}",
                        result=DiagnosticResult.PASS,
                        message=f"{lib_name} available"
                    ))
                else:
                    self._add_result(TestResult(
                        name=f"Library: {lib_name}",
                        result=DiagnosticResult.FAIL,
                        message=f"{lib_name} not available",
                        fix_suggestion=install_cmd
                    ))
            except Exception as e:
                self._add_result(TestResult(
                    name=f"Library: {lib_name}",
                    result=DiagnosticResult.FAIL,
                    message=f"{lib_name} test failed: {e}",
                    fix_suggestion=install_cmd
                ))
    
    def _test_microphone_levels(self):
        """Test microphone input levels"""
        try:
            # Test with sounddevice if available
            test_code = '''
import sys
import time
import numpy as np

try:
    import sounddevice as sd
    
    # Get device info
    device_info = sd.query_devices(kind='input')
    print(f"DEVICE:{device_info['name']}")
    
    # Test recording for 2 seconds
    duration = 2
    samplerate = 16000
    channels = 1
    
    print("RECORDING:Starting")
    
    def callback(indata, frames, time, status):
        if status:
            print(f"STATUS:{status}")
        
        volume_norm = np.linalg.norm(indata) * 10
        volume_rms = np.sqrt(np.mean(indata ** 2))
        print(f"LEVEL:{volume_rms:.6f}")
    
    with sd.InputStream(
        callback=callback,
        channels=channels,
        samplerate=samplerate,
        blocksize=1024
    ):
        sd.sleep(int(duration * 1000))
    
    print("RECORDING:Complete")
    
except ImportError:
    print("ERROR:sounddevice not available")
except Exception as e:
    print(f"ERROR:{e}")
'''
            
            result = subprocess.run([
                'python3', '-c', test_code
            ], capture_output=True, text=True, timeout=15)
            
            if result.returncode == 0:
                output_lines = result.stdout.strip().split('\n')
                device_found = False
                levels_detected = False
                max_level = 0.0
                
                for line in output_lines:
                    if line.startswith("DEVICE:"):
                        device_found = True
                        device_name = line.split(":", 1)[1]
                    elif line.startswith("LEVEL:"):
                        levels_detected = True
                        try:
                            level = float(line.split(":", 1)[1])
                            max_level = max(max_level, level)
                        except ValueError:
                            pass
                    elif line.startswith("ERROR:"):
                        error_msg = line.split(":", 1)[1]
                        self._add_result(TestResult(
                            name="Microphone Level Test",
                            result=DiagnosticResult.FAIL,
                            message=f"Audio test error: {error_msg}"
                        ))
                        return
                
                if device_found and levels_detected:
                    if max_level > 0.001:  # Threshold for detecting actual audio
                        self._add_result(TestResult(
                            name="Microphone Level Test",
                            result=DiagnosticResult.PASS,
                            message=f"Microphone levels detected (max: {max_level:.4f})",
                            details=f"Device: {device_name}"
                        ))
                    else:
                        self._add_result(TestResult(
                            name="Microphone Level Test",
                            result=DiagnosticResult.FAIL,
                            message=f"Microphone levels too low (max: {max_level:.6f})",
                            fix_suggestion="Check microphone is not muted and increase input volume",
                            details="This is likely the root cause of the 0.0000 input levels bug"
                        ))
                else:
                    self._add_result(TestResult(
                        name="Microphone Level Test",
                        result=DiagnosticResult.WARNING,
                        message="Could not complete microphone level test"
                    ))
            else:
                self._add_result(TestResult(
                    name="Microphone Level Test",
                    result=DiagnosticResult.FAIL,
                    message="Microphone level test failed",
                    details=result.stderr
                ))
                
        except Exception as e:
            self._add_result(TestResult(
                name="Microphone Level Test",
                result=DiagnosticResult.WARNING,
                message=f"Level test error: {e}"
            ))
    
    def _test_audio_devices(self):
        """Test audio device enumeration"""
        try:
            test_code = '''
import sys
try:
    import sounddevice as sd
    devices = sd.query_devices()
    input_devices = [d for d in devices if d['max_input_channels'] > 0]
    print(f"TOTAL_DEVICES:{len(devices)}")
    print(f"INPUT_DEVICES:{len(input_devices)}")
    for i, device in enumerate(input_devices):
        print(f"INPUT_{i}:{device['name']} ({device['max_input_channels']} ch)")
except ImportError:
    print("ERROR:sounddevice not available")
except Exception as e:
    print(f"ERROR:{e}")
'''
            
            result = subprocess.run([
                'python3', '-c', test_code
            ], capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                output_lines = result.stdout.strip().split('\n')
                input_device_count = 0
                
                for line in output_lines:
                    if line.startswith("INPUT_DEVICES:"):
                        input_device_count = int(line.split(":")[1])
                        break
                
                if input_device_count > 0:
                    self._add_result(TestResult(
                        name="Audio Device Enumeration",
                        result=DiagnosticResult.PASS,
                        message=f"Found {input_device_count} input device(s)",
                        details="\n".join([line for line in output_lines if line.startswith("INPUT_")])
                    ))
                else:
                    self._add_result(TestResult(
                        name="Audio Device Enumeration",
                        result=DiagnosticResult.FAIL,
                        message="No input devices found",
                        fix_suggestion="Check microphone hardware connection"
                    ))
            else:
                self._add_result(TestResult(
                    name="Audio Device Enumeration",
                    result=DiagnosticResult.WARNING,
                    message="Device enumeration failed"
                ))
                
        except Exception as e:
            self._add_result(TestResult(
                name="Audio Device Enumeration",
                result=DiagnosticResult.WARNING,
                message=f"Device enumeration error: {e}"
            ))
    
    def _test_stt_engines(self):
        """Test STT engines availability"""
        # Test if STT manager can be imported and initialized
        try:
            test_code = '''
import sys
sys.path.insert(0, 'src')

try:
    from src.engines.stt.stt_manager import STTManager
    
    manager = STTManager()
    available_engines = manager.get_available_engines()
    
    print(f"ENGINES:{len(available_engines)}")
    for engine in available_engines:
        print(f"ENGINE:{engine}")
    
    if manager.current_engine_name:
        print(f"CURRENT:{manager.current_engine_name}")
    else:
        print("CURRENT:None")
        
except ImportError as e:
    print(f"IMPORT_ERROR:{e}")
except Exception as e:
    print(f"ERROR:{e}")
'''
            
            result = subprocess.run([
                'python3', '-c', test_code
            ], capture_output=True, text=True, timeout=15)
            
            if result.returncode == 0:
                output_lines = result.stdout.strip().split('\n')
                engine_count = 0
                current_engine = None
                available_engines = []
                
                for line in output_lines:
                    if line.startswith("ENGINES:"):
                        engine_count = int(line.split(":")[1])
                    elif line.startswith("ENGINE:"):
                        available_engines.append(line.split(":", 1)[1])
                    elif line.startswith("CURRENT:"):
                        current_engine = line.split(":", 1)[1]
                        if current_engine == "None":
                            current_engine = None
                    elif line.startswith("IMPORT_ERROR:") or line.startswith("ERROR:"):
                        error_msg = line.split(":", 1)[1]
                        self._add_result(TestResult(
                            name="STT Engine Availability",
                            result=DiagnosticResult.FAIL,
                            message=f"STT engine test error: {error_msg}"
                        ))
                        return
                
                if engine_count > 0:
                    self._add_result(TestResult(
                        name="STT Engine Availability",
                        result=DiagnosticResult.PASS,
                        message=f"{engine_count} STT engine(s) available",
                        details=f"Engines: {', '.join(available_engines)}\nCurrent: {current_engine}"
                    ))
                else:
                    self._add_result(TestResult(
                        name="STT Engine Availability",
                        result=DiagnosticResult.FAIL,
                        message="No STT engines available",
                        fix_suggestion="Install STT dependencies: pip install vosk sounddevice pyobjc-framework-Speech"
                    ))
            else:
                self._add_result(TestResult(
                    name="STT Engine Availability",
                    result=DiagnosticResult.FAIL,
                    message="STT engine test failed",
                    details=result.stderr
                ))
                
        except Exception as e:
            self._add_result(TestResult(
                name="STT Engine Availability",
                result=DiagnosticResult.WARNING,
                message=f"STT engine test error: {e}"
            ))
    
    def _add_result(self, result: TestResult):
        """Add a test result and print it"""
        self.test_results.append(result)
        
        # Print result immediately
        status_icon = result.result.value
        print(f"{status_icon} {result.name}: {result.message}")
        
        if self.detailed_output and result.details:
            print(f"   Details: {result.details}")
        
        if result.fix_suggestion:
            print(f"   💡 Fix: {result.fix_suggestion}")
    
    def _print_diagnostic_summary(self):
        """Print comprehensive diagnostic summary"""
        print("📋 DIAGNOSTIC SUMMARY")
        print("=" * 70)
        
        # Count results by type
        passed = len([r for r in self.test_results if r.result == DiagnosticResult.PASS])
        failed = len([r for r in self.test_results if r.result == DiagnosticResult.FAIL])
        warnings = len([r for r in self.test_results if r.result == DiagnosticResult.WARNING])
        skipped = len([r for r in self.test_results if r.result == DiagnosticResult.SKIP])
        
        print(f"✅ Passed: {passed}")
        print(f"❌ Failed: {failed}")
        print(f"⚠️  Warnings: {warnings}")
        print(f"⏭️  Skipped: {skipped}")
        print()
        
        # Show critical failures
        critical_failures = [r for r in self.test_results if r.result == DiagnosticResult.FAIL]
        if critical_failures:
            print("🚨 CRITICAL ISSUES:")
            for failure in critical_failures:
                print(f"   ❌ {failure.name}: {failure.message}")
                if failure.fix_suggestion:
                    print(f"      💡 {failure.fix_suggestion}")
            print()
        
        # Show warnings that need attention
        important_warnings = [r for r in self.test_results if r.result == DiagnosticResult.WARNING]
        if important_warnings:
            print("⚠️  WARNINGS:")
            for warning in important_warnings:
                print(f"   ⚠️  {warning.name}: {warning.message}")
                if warning.fix_suggestion:
                    print(f"      💡 {warning.fix_suggestion}")
            print()
    
    def _suggest_automated_fixes(self):
        """Suggest automated fixes for common issues"""
        print("🔧 AUTOMATED FIX SUGGESTIONS")
        print("=" * 70)
        
        # Collect all fix suggestions
        fixes = [r.fix_suggestion for r in self.test_results if r.fix_suggestion]
        
        if not fixes:
            print("✅ No automated fixes needed!")
            return
        
        # Group by type of fix
        pip_installs = [f for f in fixes if f.startswith("pip install")]
        system_changes = [f for f in fixes if "System Preferences" in f or "settings" in f.lower()]
        other_fixes = [f for f in fixes if f not in pip_installs + system_changes]
        
        if pip_installs:
            print("📦 Install missing Python packages:")
            unique_installs = list(set(pip_installs))
            for install_cmd in unique_installs:
                print(f"   {install_cmd}")
            print()
        
        if system_changes:
            print("🔐 System configuration changes:")
            unique_changes = list(set(system_changes))
            for change in unique_changes:
                print(f"   • {change}")
            print()
        
        if other_fixes:
            print("🛠️  Other fixes:")
            unique_other = list(set(other_fixes))
            for fix in unique_other:
                print(f"   • {fix}")
            print()
    
    def _calculate_overall_health(self) -> bool:
        """Calculate overall system health"""
        critical_failures = [r for r in self.test_results if r.result == DiagnosticResult.FAIL]
        
        # System is healthy if no critical failures
        return len(critical_failures) == 0
    
    def _get_home_directory(self) -> str:
        """Get user home directory"""
        import os
        return os.path.expanduser("~")


def main():
    """Main diagnostic function"""
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 Microphone Doctor - Comprehensive diagnostic tool")
    parser.add_argument("--detailed", "-d", action="store_true", help="Show detailed output")
    parser.add_argument("--fix", "-f", action="store_true", help="Attempt automated fixes")
    args = parser.parse_args()
    
    doctor = MicrophoneDoctor()
    is_healthy = doctor.run_full_diagnostic(detailed=args.detailed)
    
    print()
    if is_healthy:
        print("🎉 DIAGNOSIS COMPLETE: System appears healthy!")
        print("   If you're still experiencing issues, try running:")
        print("   python stt_diagnostics.py")
        exit_code = 0
    else:
        print("🚨 DIAGNOSIS COMPLETE: Critical issues found!")
        print("   Follow the fix suggestions above to resolve issues.")
        print("   Re-run this diagnostic after making changes.")
        exit_code = 1
    
    if args.fix:
        print("\n🔧 Automated fix mode not yet implemented")
        print("   Please follow the manual fix suggestions above")
    
    return exit_code


if __name__ == "__main__":
    exit(main())