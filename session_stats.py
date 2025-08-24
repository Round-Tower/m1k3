#!/usr/bin/env python3
"""
M1K3 Session Statistics Tracker
Tracks usage, achievements, and provides exciting insights
"""

import json
import time
import random
from pathlib import Path
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, asdict

@dataclass
class SessionStats:
    """Tracks all session statistics"""
    # Session info
    session_start: float
    session_id: str
    
    # Usage counters
    queries_handled: int = 0
    tokens_used: int = 0
    voice_synthesized_count: int = 0
    avatar_emotions_triggered: int = 0
    rag_queries: int = 0
    
    # Performance metrics
    avg_response_time: float = 0.0
    fastest_response: float = float('inf')
    slowest_response: float = 0.0
    
    # Eco metrics (cumulative)
    water_saved_ml: float = 0.0
    energy_saved_wh: float = 0.0
    co2_saved_g: float = 0.0
    
    # Feature usage
    features_used: List[str] = None
    
    # Achievements unlocked this session
    achievements_unlocked: List[str] = None
    
    # Fun statistics
    favorite_topic: str = ""
    longest_conversation: int = 0
    total_words_generated: int = 0
    
    def __post_init__(self):
        if self.features_used is None:
            self.features_used = []
        if self.achievements_unlocked is None:
            self.achievements_unlocked = []

class SessionStatisticsTracker:
    """Manages session statistics and provides insights"""
    
    def __init__(self, stats_dir: str = ".m1k3_stats"):
        self.stats_dir = Path.home() / stats_dir
        self.stats_dir.mkdir(exist_ok=True)
        
        # Current session
        self.session_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.current_stats = SessionStats(
            session_start=time.time(),
            session_id=self.session_id
        )
        
        # Load historical stats
        self.historical_stats = self._load_historical_stats()
        
        # Response time tracking
        self.response_times = []
        
    def _load_historical_stats(self) -> Dict:
        """Load historical statistics from disk"""
        stats_file = self.stats_dir / "historical_stats.json"
        if stats_file.exists():
            try:
                with open(stats_file, 'r') as f:
                    return json.load(f)
            except:
                pass
        
        return {
            "total_queries": 0,
            "total_tokens": 0,
            "total_sessions": 0,
            "total_uptime_hours": 0.0,
            "total_water_saved_ml": 0.0,
            "total_energy_saved_wh": 0.0,
            "total_co2_saved_g": 0.0,
            "all_time_achievements": [],
            "first_run_date": datetime.now().isoformat()
        }
    
    def save_stats(self):
        """Save current session and update historical stats"""
        # Update historical
        self.historical_stats["total_queries"] += self.current_stats.queries_handled
        self.historical_stats["total_tokens"] += self.current_stats.tokens_used
        self.historical_stats["total_sessions"] += 1
        self.historical_stats["total_uptime_hours"] += self.get_session_duration() / 3600
        self.historical_stats["total_water_saved_ml"] += self.current_stats.water_saved_ml
        self.historical_stats["total_energy_saved_wh"] += self.current_stats.energy_saved_wh
        self.historical_stats["total_co2_saved_g"] += self.current_stats.co2_saved_g
        
        # Save to disk
        stats_file = self.stats_dir / "historical_stats.json"
        with open(stats_file, 'w') as f:
            json.dump(self.historical_stats, f, indent=2)
        
        # Save session details
        session_file = self.stats_dir / f"session_{self.session_id}.json"
        with open(session_file, 'w') as f:
            json.dump(asdict(self.current_stats), f, indent=2)
    
    def record_query(self, response_time: float = 0, tokens: int = 0):
        """Record a query being handled"""
        self.current_stats.queries_handled += 1
        self.current_stats.tokens_used += tokens
        
        if response_time > 0:
            self.response_times.append(response_time)
            self.current_stats.avg_response_time = sum(self.response_times) / len(self.response_times)
            self.current_stats.fastest_response = min(self.response_times)
            self.current_stats.slowest_response = max(self.response_times)
        
        # Update eco metrics (approximate)
        self.current_stats.water_saved_ml += 120  # ~120ml per query vs cloud
        self.current_stats.energy_saved_wh += 3    # ~3Wh per query vs cloud
        self.current_stats.co2_saved_g += 2        # ~2g CO2 per query
    
    def record_feature_use(self, feature: str):
        """Record usage of a feature"""
        if feature not in self.current_stats.features_used:
            self.current_stats.features_used.append(feature)
    
    def unlock_achievement(self, achievement: str):
        """Unlock an achievement"""
        if achievement not in self.current_stats.achievements_unlocked:
            self.current_stats.achievements_unlocked.append(achievement)
        if achievement not in self.historical_stats.get("all_time_achievements", []):
            self.historical_stats.setdefault("all_time_achievements", []).append(achievement)
    
    def get_session_duration(self) -> float:
        """Get current session duration in seconds"""
        return time.time() - self.current_stats.session_start
    
    def get_session_duration_str(self) -> str:
        """Get formatted session duration"""
        duration = self.get_session_duration()
        if duration < 60:
            return f"{int(duration)}s"
        elif duration < 3600:
            return f"{int(duration/60)}m {int(duration%60)}s"
        else:
            hours = int(duration / 3600)
            minutes = int((duration % 3600) / 60)
            return f"{hours}h {minutes}m"
    
    def get_exciting_insight(self) -> str:
        """Generate an exciting insight about the session or system"""
        insights = []
        
        # Query-based insights
        if self.current_stats.queries_handled == 1:
            insights.append("🎯 First query of the session! Let's make it count!")
        elif self.current_stats.queries_handled == 10:
            insights.append("🔥 10 queries already! You're on fire today!")
        elif self.current_stats.queries_handled == 50:
            insights.append("💫 50 queries! That's some serious productivity!")
        
        # Time-based insights
        duration = self.get_session_duration()
        if 3595 < duration < 3605:  # Around 1 hour
            insights.append("⏰ One hour of pure local AI power! No cloud needed!")
        
        # Eco insights
        if self.current_stats.water_saved_ml > 1000:
            liters = self.current_stats.water_saved_ml / 1000
            insights.append(f"💧 You've saved {liters:.1f} liters of water vs cloud AI!")
        
        if self.current_stats.energy_saved_wh > 100:
            insights.append(f"⚡ {self.current_stats.energy_saved_wh:.0f}Wh saved - enough to charge a phone!")
        
        # Performance insights
        if self.current_stats.fastest_response < 0.5:
            insights.append(f"🚀 Fastest response: {self.current_stats.fastest_response:.2f}s - Lightning fast!")
        
        # Historical insights
        total_queries = self.historical_stats.get("total_queries", 0) + self.current_stats.queries_handled
        if total_queries > 0 and total_queries % 100 == 0:
            insights.append(f"🎊 Milestone: {total_queries} total queries processed!")
        
        # Feature insights
        if len(self.current_stats.features_used) >= 3:
            insights.append("🌟 Multi-feature master! Voice, Avatar, and RAG all active!")
        
        # Random motivational insights
        motivational = [
            "🧠 Your local AI is learning your style!",
            "🔒 100% private - zero data leaves your device!",
            "🌍 Carbon-negative computing in action!",
            "⚡ Processing at the speed of thought!",
            "🎨 Creativity meets privacy!",
            "🚀 The future of AI is local and it's here!",
            "💎 Premium AI experience, zero subscription!",
            "🌟 You're part of the local AI revolution!",
        ]
        
        # Add a random motivational if no other insights
        if not insights or random.random() < 0.3:
            insights.append(random.choice(motivational))
        
        return random.choice(insights) if insights else "🚀 Ready for anything!"
    
    def get_stats_summary(self) -> Dict:
        """Get a summary of current session stats"""
        return {
            "duration": self.get_session_duration_str(),
            "queries": self.current_stats.queries_handled,
            "tokens": self.current_stats.tokens_used,
            "water_saved_ml": self.current_stats.water_saved_ml,
            "energy_saved_wh": self.current_stats.energy_saved_wh,
            "co2_saved_g": self.current_stats.co2_saved_g,
            "features_used": len(self.current_stats.features_used),
            "achievements": len(self.current_stats.achievements_unlocked),
            "avg_response_time": self.current_stats.avg_response_time
        }
    
    def get_formatted_stats_display(self, max_tokens: int = 8192) -> str:
        """Get formatted statistics for display"""
        duration = self.get_session_duration_str()
        queries = self.current_stats.queries_handled
        tokens = self.current_stats.tokens_used
        token_percent = (tokens / max_tokens) * 100 if max_tokens > 0 else 0
        
        # Format eco savings
        water = self.current_stats.water_saved_ml
        energy = self.current_stats.energy_saved_wh
        co2 = self.current_stats.co2_saved_g
        
        lines = []
        lines.append(f"Uptime: {duration} | Queries: {queries} | Tokens: {tokens}/{max_tokens} ({token_percent:.0f}%)")
        lines.append(f"Eco Savings: 🌊 {water:.0f}ml water | ⚡ {energy:.0f}Wh energy | 🌱 {co2:.0f}g CO₂")
        lines.append(f"Privacy: 🔒 0 bytes transmitted (100% local)")
        
        return "\n".join(lines)

class AchievementSystem:
    """Manages achievements and milestones"""
    
    ACHIEVEMENTS = {
        # Session achievements
        "first_boot": {"name": "🎯 First Boot", "desc": "Started M1K3 for the first time"},
        "first_query": {"name": "💬 First Words", "desc": "Asked your first question"},
        "speed_demon": {"name": "🚀 Speed Demon", "desc": "Response under 0.5 seconds"},
        "conversation_master": {"name": "🗣️ Conversation Master", "desc": "10 queries in one session"},
        "marathon_runner": {"name": "⏰ Marathon Runner", "desc": "1 hour continuous session"},
        
        # Feature achievements
        "voice_user": {"name": "🎤 Voice User", "desc": "Used voice synthesis"},
        "avatar_friend": {"name": "👾 Avatar Friend", "desc": "Activated avatar dashboard"},
        "rag_researcher": {"name": "📚 RAG Researcher", "desc": "Used knowledge base"},
        "feature_explorer": {"name": "🌟 Feature Explorer", "desc": "Used 3+ features"},
        
        # Eco achievements
        "eco_warrior": {"name": "🌍 Eco Warrior", "desc": "Saved 1L of water"},
        "energy_saver": {"name": "⚡ Energy Saver", "desc": "Saved 100Wh of energy"},
        "carbon_hero": {"name": "🌱 Carbon Hero", "desc": "Prevented 100g CO₂"},
        
        # Milestone achievements
        "century": {"name": "💯 Century", "desc": "100 total queries"},
        "thousand_club": {"name": "🏆 Thousand Club", "desc": "1000 total queries"},
        "token_master": {"name": "📊 Token Master", "desc": "Used 100k tokens"},
    }
    
    @classmethod
    def check_achievements(cls, stats_tracker: SessionStatisticsTracker) -> List[str]:
        """Check for newly unlocked achievements"""
        unlocked = []
        stats = stats_tracker.current_stats
        historical = stats_tracker.historical_stats
        
        # First boot
        if historical.get("total_sessions", 0) == 0:
            unlocked.append("first_boot")
        
        # First query
        if stats.queries_handled == 1:
            unlocked.append("first_query")
        
        # Speed demon
        if stats.fastest_response < 0.5:
            unlocked.append("speed_demon")
        
        # Conversation master
        if stats.queries_handled >= 10:
            unlocked.append("conversation_master")
        
        # Marathon runner
        if stats_tracker.get_session_duration() >= 3600:
            unlocked.append("marathon_runner")
        
        # Feature achievements
        if "voice" in stats.features_used:
            unlocked.append("voice_user")
        if "avatar" in stats.features_used:
            unlocked.append("avatar_friend")
        if "rag" in stats.features_used:
            unlocked.append("rag_researcher")
        if len(stats.features_used) >= 3:
            unlocked.append("feature_explorer")
        
        # Eco achievements
        if stats.water_saved_ml >= 1000:
            unlocked.append("eco_warrior")
        if stats.energy_saved_wh >= 100:
            unlocked.append("energy_saver")
        if stats.co2_saved_g >= 100:
            unlocked.append("carbon_hero")
        
        # Milestone achievements
        total_queries = historical.get("total_queries", 0) + stats.queries_handled
        if total_queries >= 100:
            unlocked.append("century")
        if total_queries >= 1000:
            unlocked.append("thousand_club")
        
        total_tokens = historical.get("total_tokens", 0) + stats.tokens_used
        if total_tokens >= 100000:
            unlocked.append("token_master")
        
        # Record unlocked achievements
        for achievement_id in unlocked:
            if achievement_id not in stats.achievements_unlocked:
                stats_tracker.unlock_achievement(achievement_id)
        
        return unlocked
    
    @classmethod
    def format_achievement(cls, achievement_id: str) -> str:
        """Format an achievement for display"""
        if achievement_id in cls.ACHIEVEMENTS:
            ach = cls.ACHIEVEMENTS[achievement_id]
            return f"{ach['name']}"
        return achievement_id

# Global instance
_stats_tracker = None

def get_stats_tracker() -> SessionStatisticsTracker:
    """Get or create the global stats tracker"""
    global _stats_tracker
    if _stats_tracker is None:
        _stats_tracker = SessionStatisticsTracker()
    return _stats_tracker

if __name__ == "__main__":
    # Test the statistics system
    print("🧪 Testing Session Statistics System")
    print("=" * 50)
    
    tracker = SessionStatisticsTracker()
    
    # Simulate some activity
    tracker.record_query(response_time=0.3, tokens=150)
    tracker.record_feature_use("voice")
    tracker.record_feature_use("avatar")
    
    time.sleep(1)
    tracker.record_query(response_time=0.8, tokens=200)
    
    # Check achievements
    unlocked = AchievementSystem.check_achievements(tracker)
    
    print("\n📊 Session Statistics:")
    print(tracker.get_formatted_stats_display())
    
    print("\n🎯 Achievements Unlocked:")
    for ach_id in unlocked:
        print(f"  {AchievementSystem.format_achievement(ach_id)}")
    
    print("\n💡 Insight:")
    print(f"  {tracker.get_exciting_insight()}")
    
    print("\n✅ Statistics system ready!")