#!/usr/bin/env python3
"""
Conversation Analytics - Advanced Query Interface
Provides insights, analytics, and advanced queries for M1K3 conversations
"""

import json
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass
from .conversation_manager import get_conversation_manager

@dataclass
class ConversationInsight:
    """Represents an analytical insight about conversations"""
    title: str
    description: str
    value: Any
    trend: Optional[str] = None  # "up", "down", "stable"
    category: str = "general"

class ConversationAnalytics:
    """Advanced analytics and insights for conversation data"""

    def __init__(self):
        self.manager = get_conversation_manager()

    def get_usage_insights(self, days: int = 30) -> List[ConversationInsight]:
        """Generate usage insights for the past N days"""
        insights = []

        # Get basic stats
        stats = self.manager.get_conversation_stats(days)

        if stats['total_conversations'] > 0:
            insights.append(ConversationInsight(
                title="📊 Total Conversations",
                description=f"Total conversations in last {days} days",
                value=stats['total_conversations'],
                category="usage"
            ))

            insights.append(ConversationInsight(
                title="🗓️ Daily Average",
                description="Average conversations per day",
                value=round(stats['total_conversations'] / days, 1),
                category="usage"
            ))

            insights.append(ConversationInsight(
                title="⚡ Avg Response Time",
                description="Average AI response time",
                value=f"{stats['avg_response_time']:.0f}ms",
                category="performance"
            ))

            # Feature usage insights
            if stats['voice_conversations'] > 0:
                voice_percent = (stats['voice_conversations'] / stats['total_conversations']) * 100
                insights.append(ConversationInsight(
                    title="🎤 Voice Usage",
                    description="Conversations with voice synthesis",
                    value=f"{voice_percent:.1f}% ({stats['voice_conversations']} conversations)",
                    category="features"
                ))

            if stats['avatar_conversations'] > 0:
                avatar_percent = (stats['avatar_conversations'] / stats['total_conversations']) * 100
                insights.append(ConversationInsight(
                    title="👾 Avatar Usage",
                    description="Conversations with avatar active",
                    value=f"{avatar_percent:.1f}% ({stats['avatar_conversations']} conversations)",
                    category="features"
                ))

            if stats['rag_conversations'] > 0:
                rag_percent = (stats['rag_conversations'] / stats['total_conversations']) * 100
                insights.append(ConversationInsight(
                    title="📚 RAG Usage",
                    description="Conversations using knowledge base",
                    value=f"{rag_percent:.1f}% ({stats['rag_conversations']} conversations)",
                    category="features"
                ))

            # Eco insights
            if stats['total_tokens'] > 0:
                # Rough calculations based on cloud AI comparisons
                water_saved_ml = stats['total_conversations'] * 120  # ~120ml per query vs cloud
                energy_saved_wh = stats['total_conversations'] * 3    # ~3Wh per query vs cloud
                co2_saved_g = stats['total_conversations'] * 2        # ~2g CO2 per query

                insights.append(ConversationInsight(
                    title="💧 Water Saved",
                    description="Water conservation vs cloud AI",
                    value=f"{water_saved_ml/1000:.1f}L" if water_saved_ml >= 1000 else f"{water_saved_ml:.0f}ml",
                    category="eco"
                ))

                insights.append(ConversationInsight(
                    title="⚡ Energy Saved",
                    description="Energy conservation vs cloud AI",
                    value=f"{energy_saved_wh:.0f}Wh",
                    category="eco"
                ))

                insights.append(ConversationInsight(
                    title="🌱 CO₂ Prevented",
                    description="Carbon emissions prevented",
                    value=f"{co2_saved_g:.0f}g CO₂",
                    category="eco"
                ))

        return insights

    def get_personality_insights(self, days: int = 30) -> List[ConversationInsight]:
        """Analyze personality usage patterns"""
        insights = []

        result = self.manager.conn.execute("""
            SELECT personality_type, COUNT(*) as count,
                   AVG(response_time_ms) as avg_response_time,
                   SUM(tokens_used) as total_tokens
            FROM conversations
            WHERE timestamp >= ?
            GROUP BY personality_type
            ORDER BY count DESC
        """, [datetime.now() - timedelta(days=days)])

        personalities = [dict(zip([col[0] for col in result.description], row))
                        for row in result.fetchall()]

        if personalities:
            top_personality = personalities[0]
            insights.append(ConversationInsight(
                title="🎭 Most Used Personality",
                description="Your preferred AI personality",
                value=f"{top_personality['personality_type']} ({top_personality['count']} conversations)",
                category="personality"
            ))

            if len(personalities) > 1:
                insights.append(ConversationInsight(
                    title="🔄 Personality Variety",
                    description="Different personalities used",
                    value=f"{len(personalities)} different types",
                    category="personality"
                ))

        return insights

    def get_time_patterns(self, days: int = 30) -> Dict:
        """Analyze conversation time patterns"""
        result = self.manager.conn.execute("""
            SELECT
                strftime('%H', timestamp) as hour,
                strftime('%w', timestamp) as day_of_week,
                COUNT(*) as count
            FROM conversations
            WHERE timestamp >= ?
            GROUP BY hour, day_of_week
            ORDER BY count DESC
        """, [datetime.now() - timedelta(days=days)])

        time_data = [dict(zip([col[0] for col in result.description], row))
                    for row in result.fetchall()]

        # Find peak hours
        hourly_counts = {}
        daily_counts = {}

        for row in time_data:
            hour = int(row['hour'])
            day = int(row['day_of_week'])
            count = row['count']

            hourly_counts[hour] = hourly_counts.get(hour, 0) + count
            daily_counts[day] = daily_counts.get(day, 0) + count

        peak_hour = max(hourly_counts, key=hourly_counts.get) if hourly_counts else 12
        peak_day = max(daily_counts, key=daily_counts.get) if daily_counts else 0

        day_names = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

        return {
            "peak_hour": peak_hour,
            "peak_hour_12": f"{peak_hour % 12 or 12}{'PM' if peak_hour >= 12 else 'AM'}",
            "peak_day": day_names[peak_day] if 0 <= peak_day < 7 else "Unknown",
            "hourly_distribution": hourly_counts,
            "daily_distribution": daily_counts
        }

    def get_response_time_analysis(self, days: int = 30) -> Dict:
        """Analyze response time patterns"""
        result = self.manager.conn.execute("""
            SELECT
                MIN(response_time_ms) as fastest,
                MAX(response_time_ms) as slowest,
                AVG(response_time_ms) as average,
                personality_type,
                voice_enabled,
                avatar_active,
                rag_used
            FROM conversations
            WHERE timestamp >= ?
            GROUP BY personality_type, voice_enabled, avatar_active, rag_used
        """, [datetime.now() - timedelta(days=days)])

        performance_data = [dict(zip([col[0] for col in result.description], row))
                           for row in result.fetchall()]

        # Find fastest and slowest configurations
        if performance_data:
            fastest_config = min(performance_data, key=lambda x: x['average'])
            slowest_config = max(performance_data, key=lambda x: x['average'])

            return {
                "fastest_avg_config": fastest_config,
                "slowest_avg_config": slowest_config,
                "performance_breakdown": performance_data
            }

        return {}

    def get_conversation_topics(self, days: int = 30, limit: int = 10) -> List[Dict]:
        """Analyze common conversation topics (basic keyword analysis)"""
        result = self.manager.conn.execute("""
            SELECT user_input, ai_response, timestamp, personality_type
            FROM conversations
            WHERE timestamp >= ?
            ORDER BY timestamp DESC
        """, [datetime.now() - timedelta(days=days)])

        conversations = [dict(zip([col[0] for col in result.description], row))
                        for row in result.fetchall()]

        # Simple keyword extraction (basic implementation)
        common_words = {}
        skip_words = {'the', 'is', 'at', 'which', 'on', 'and', 'or', 'but', 'in', 'with', 'a', 'an',
                     'to', 'for', 'of', 'as', 'by', 'i', 'you', 'me', 'we', 'they', 'this', 'that',
                     'what', 'where', 'when', 'why', 'how', 'can', 'could', 'would', 'should',
                     'do', 'does', 'did', 'will', 'have', 'has', 'had', 'be', 'been', 'being'}

        for conv in conversations:
            words = conv['user_input'].lower().split()
            for word in words:
                # Clean word
                word = ''.join(c for c in word if c.isalnum())
                if len(word) > 3 and word not in skip_words:
                    common_words[word] = common_words.get(word, 0) + 1

        # Get top words
        top_topics = sorted(common_words.items(), key=lambda x: x[1], reverse=True)[:limit]

        return [{"topic": topic, "count": count} for topic, count in top_topics]

    def generate_conversation_report(self, days: int = 30) -> str:
        """Generate a comprehensive conversation report"""
        insights = self.get_usage_insights(days)
        personality_insights = self.get_personality_insights(days)
        time_patterns = self.get_time_patterns(days)
        performance = self.get_response_time_analysis(days)
        topics = self.get_conversation_topics(days, 5)

        report_lines = [
            f"📊 M1K3 Conversation Analytics Report",
            f"=" * 50,
            f"📅 Period: Last {days} days",
            f"📈 Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            "",
        ]

        # Usage insights
        if insights:
            report_lines.append("💬 USAGE INSIGHTS")
            report_lines.append("-" * 30)
            for insight in insights:
                report_lines.append(f"{insight.title}: {insight.value}")
                if insight.description:
                    report_lines.append(f"   {insight.description}")
            report_lines.append("")

        # Personality insights
        if personality_insights:
            report_lines.append("🎭 PERSONALITY INSIGHTS")
            report_lines.append("-" * 30)
            for insight in personality_insights:
                report_lines.append(f"{insight.title}: {insight.value}")
            report_lines.append("")

        # Time patterns
        if time_patterns:
            report_lines.append("⏰ TIME PATTERNS")
            report_lines.append("-" * 30)
            report_lines.append(f"🕐 Peak Hour: {time_patterns['peak_hour_12']}")
            report_lines.append(f"📅 Peak Day: {time_patterns['peak_day']}")
            report_lines.append("")

        # Performance analysis
        if performance:
            report_lines.append("⚡ PERFORMANCE ANALYSIS")
            report_lines.append("-" * 30)
            fastest = performance.get('fastest_avg_config')
            if fastest:
                report_lines.append(f"🚀 Fastest Config: {fastest['personality_type']} "
                                  f"({fastest['average']:.0f}ms avg)")
            report_lines.append("")

        # Common topics
        if topics:
            report_lines.append("📝 COMMON TOPICS")
            report_lines.append("-" * 30)
            for i, topic_data in enumerate(topics, 1):
                report_lines.append(f"{i}. {topic_data['topic']} ({topic_data['count']} times)")
            report_lines.append("")

        report_lines.extend([
            "🎯 RECOMMENDATIONS",
            "-" * 30,
            "• Your M1K3 is working great with local processing!",
            "• All conversations are private and eco-friendly",
            "• Continue exploring different personalities and features",
            "",
            "Generated by M1K3 Analytics Engine 🤖"
        ])

        return "\n".join(report_lines)

    def run_custom_query(self, sql_query: str) -> List[Dict]:
        """Run a custom SQL query on the conversation database"""
        try:
            result = self.manager.conn.execute(sql_query)
            return [dict(zip([col[0] for col in result.description], row))
                   for row in result.fetchall()]
        except Exception as e:
            raise ValueError(f"SQL query failed: {e}")

# Global instance for easy access
_conversation_analytics = None

def get_conversation_analytics() -> ConversationAnalytics:
    """Get or create global conversation analytics instance"""
    global _conversation_analytics
    if _conversation_analytics is None:
        _conversation_analytics = ConversationAnalytics()
    return _conversation_analytics