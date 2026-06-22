#!/usr/bin/env python3
"""
Voice Truncation Tracker - Enhanced Diagnostics
Tracks truncation fix effectiveness and provides detailed analytics
"""

import time
import json
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any
import numpy as np

class TruncationTracker:
    """
    Tracks voice truncation detection and fixes for analysis and improvement.
    """
    
    def __init__(self, log_file: str = "truncation_log.json"):
        self.log_file = log_file
        self.session_data = {
            "session_start": datetime.now().isoformat(),
            "detections": [],
            "fixes_applied": 0,
            "total_audio_processed": 0,
            "detection_rate": 0.0,
            "average_confidence": 0.0,
            "fix_effectiveness": {}
        }
        
    def log_truncation_check(self, 
                           audio_length: int,
                           truncated: bool, 
                           confidence: float,
                           applied_fix: bool,
                           added_ms: float = 0.0,
                           debug_label: str = ""):
        """Log a truncation detection event"""
        
        event = {
            "timestamp": datetime.now().isoformat(),
            "audio_length_samples": audio_length,
            "audio_duration_ms": (audio_length / 24000) * 1000,  # Assuming 24kHz
            "truncated": truncated,
            "confidence": confidence,
            "applied_fix": applied_fix,
            "added_ms": added_ms,
            "debug_label": debug_label
        }
        
        self.session_data["detections"].append(event)
        self.session_data["total_audio_processed"] += 1
        
        if applied_fix:
            self.session_data["fixes_applied"] += 1
            
        # Update statistics
        self._update_statistics()
        
        # Print enhanced diagnostic info
        if truncated:
            print(f"📊 [TRUNCATION] {debug_label}: {confidence:.2f} confidence, "
                  f"{'FIXED' if applied_fix else 'NOT FIXED'}" + 
                  (f" (+{added_ms:.0f}ms)" if applied_fix else ""))
    
    def _update_statistics(self):
        """Update running statistics"""
        detections = self.session_data["detections"]
        
        if detections:
            # Detection rate
            truncated_count = sum(1 for d in detections if d["truncated"])
            self.session_data["detection_rate"] = truncated_count / len(detections)
            
            # Average confidence for truncated audio
            truncated_detections = [d for d in detections if d["truncated"]]
            if truncated_detections:
                self.session_data["average_confidence"] = sum(d["confidence"] for d in truncated_detections) / len(truncated_detections)
    
    def get_session_summary(self) -> Dict[str, Any]:
        """Get comprehensive session summary"""
        detections = self.session_data["detections"]
        
        if not detections:
            return {"message": "No audio processed in this session"}
        
        # Analyze by debug label
        by_label = {}
        for detection in detections:
            label = detection.get("debug_label", "unknown")
            if label not in by_label:
                by_label[label] = {"total": 0, "truncated": 0, "fixed": 0}
            
            by_label[label]["total"] += 1
            if detection["truncated"]:
                by_label[label]["truncated"] += 1
            if detection["applied_fix"]:
                by_label[label]["fixed"] += 1
        
        # Calculate fix effectiveness
        for label in by_label:
            stats = by_label[label]
            stats["truncation_rate"] = stats["truncated"] / stats["total"] if stats["total"] > 0 else 0
            stats["fix_rate"] = stats["fixed"] / stats["truncated"] if stats["truncated"] > 0 else 0
        
        # Overall statistics
        total_truncated = sum(1 for d in detections if d["truncated"])
        total_fixed = sum(1 for d in detections if d["applied_fix"])
        
        summary = {
            "session_duration": (datetime.now() - datetime.fromisoformat(self.session_data["session_start"])).total_seconds(),
            "total_audio_chunks": len(detections),
            "truncated_chunks": total_truncated,
            "fixed_chunks": total_fixed,
            "overall_truncation_rate": total_truncated / len(detections) * 100,
            "fix_success_rate": total_fixed / total_truncated * 100 if total_truncated > 0 else 0,
            "by_component": by_label,
            "confidence_stats": {
                "min": min(d["confidence"] for d in detections if d["truncated"]) if total_truncated > 0 else 0,
                "max": max(d["confidence"] for d in detections if d["truncated"]) if total_truncated > 0 else 0,
                "avg": sum(d["confidence"] for d in detections if d["truncated"]) / total_truncated if total_truncated > 0 else 0
            }
        }
        
        return summary
    
    def print_session_report(self):
        """Print a detailed session report"""
        summary = self.get_session_summary()
        
        if "message" in summary:
            print(f"📊 {summary['message']}")
            return
        
        print("\n" + "📊" * 30)
        print("   VOICE TRUNCATION SESSION REPORT")
        print("📊" * 30)
        
        print(f"\n🕒 SESSION OVERVIEW:")
        print(f"   Duration: {summary['session_duration']:.1f} seconds")
        print(f"   Audio chunks processed: {summary['total_audio_chunks']}")
        print(f"   Truncated chunks found: {summary['truncated_chunks']}")
        print(f"   Fixes applied: {summary['fixed_chunks']}")
        
        print(f"\n📈 PERFORMANCE METRICS:")
        print(f"   Overall truncation rate: {summary['overall_truncation_rate']:.1f}%")
        print(f"   Fix success rate: {summary['fix_success_rate']:.1f}%")
        
        print(f"\n🔍 CONFIDENCE ANALYSIS:")
        conf = summary['confidence_stats']
        print(f"   Min confidence: {conf['min']:.2f}")
        print(f"   Max confidence: {conf['max']:.2f}")
        print(f"   Avg confidence: {conf['avg']:.2f}")
        
        print(f"\n🔧 BY COMPONENT:")
        for label, stats in summary['by_component'].items():
            print(f"   {label}:")
            print(f"      Total: {stats['total']}, Truncated: {stats['truncated']}, Fixed: {stats['fixed']}")
            print(f"      Truncation rate: {stats['truncation_rate']*100:.1f}%, Fix rate: {stats['fix_rate']*100:.1f}%")
    
    def save_session_log(self):
        """Save session data to file"""
        # Add summary to session data
        self.session_data.update(self.get_session_summary())
        
        # Load existing log or create new
        log_path = Path(self.log_file)
        if log_path.exists():
            with open(log_path, 'r') as f:
                log_data = json.load(f)
        else:
            log_data = {"sessions": []}
        
        # Add this session
        log_data["sessions"].append(self.session_data)
        
        # Save updated log with JSON serialization fix
        with open(log_path, 'w') as f:
            json.dump(log_data, f, indent=2, default=str)
        
        print(f"💾 Session log saved to: {log_path}")

# Global tracker instance
_tracker = None

def get_truncation_tracker() -> TruncationTracker:
    """Get the global truncation tracker instance"""
    global _tracker
    if _tracker is None:
        _tracker = TruncationTracker()
    return _tracker

def log_truncation_event(audio_length: int, truncated: bool, confidence: float, 
                        applied_fix: bool, added_ms: float = 0.0, debug_label: str = ""):
    """Convenience function to log truncation events"""
    tracker = get_truncation_tracker()
    tracker.log_truncation_check(audio_length, truncated, confidence, applied_fix, added_ms, debug_label)

if __name__ == "__main__":
    # Test the tracker
    print("🧪 Testing Truncation Tracker...")
    
    tracker = TruncationTracker("test_truncation_log.json")
    
    # Simulate some events
    tracker.log_truncation_check(24000, True, 0.75, True, 300, "test_chunk_1")
    tracker.log_truncation_check(18000, False, 0.25, False, 0, "test_chunk_2") 
    tracker.log_truncation_check(32000, True, 0.85, True, 450, "combined_audio")
    tracker.log_truncation_check(28000, False, 0.15, False, 0, "post_effects")
    
    # Print report
    tracker.print_session_report()
    
    # Save log
    tracker.save_session_log()
    
    print("\n✅ Truncation tracker test completed!")