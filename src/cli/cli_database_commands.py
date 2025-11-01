#!/usr/bin/env python3
"""
CLI Database Commands Module
Database-related commands for conversation management and analytics
"""

import os
import json
from datetime import datetime, timedelta
from typing import Dict, List, Optional

from .cli_logging import get_cli_logger, log_info, log_debug, log_error

try:
    from src.database.conversation_manager import get_conversation_manager, ConversationRecord
    from src.database.conversation_analytics import get_conversation_analytics
    DATABASE_AVAILABLE = True
except ImportError as e:
    DATABASE_AVAILABLE = False
    IMPORT_ERROR = str(e)

class DatabaseCommandHandler:
    """Handles database-related CLI commands"""

    def __init__(self, cli_instance):
        self.cli = cli_instance
        self.logger = get_cli_logger()

        if not DATABASE_AVAILABLE:
            self.logger.warning(f"Database functionality not available: {IMPORT_ERROR}")

    def handle_conversations_command(self, args: List[str]) -> str:
        """Handle 'conversations' command - list recent conversations"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        try:
            manager = get_conversation_manager()

            # Parse arguments
            limit = 20
            if args and args[0].isdigit():
                limit = int(args[0])

            conversations = manager.get_recent_conversations(limit)

            if not conversations:
                return "📝 No conversations found in database."

            # Format conversations
            lines = [f"📊 Recent Conversations (showing {len(conversations)} of {limit} requested)"]
            lines.append("=" * 70)

            for i, conv in enumerate(conversations, 1):
                timestamp = conv['timestamp']
                if isinstance(timestamp, str):
                    timestamp = datetime.fromisoformat(timestamp)

                time_str = timestamp.strftime("%Y-%m-%d %H:%M:%S")
                user_preview = conv['user_input'][:50] + "..." if len(conv['user_input']) > 50 else conv['user_input']
                ai_preview = conv['ai_response'][:80] + "..." if len(conv['ai_response']) > 80 else conv['ai_response']

                lines.append(f"🗨️  {i}. {time_str}")
                lines.append(f"   👤 User: {user_preview}")
                lines.append(f"   🤖 M1K3: {ai_preview}")
                lines.append(f"   ⚡ {conv['response_time_ms']}ms • 🎭 {conv['personality_type']} • 🎤 {'✅' if conv['voice_enabled'] else '❌'}")
                lines.append("")

            return "\n".join(lines)

        except Exception as e:
            log_error(f"Failed to list conversations: {e}")
            return f"❌ Failed to retrieve conversations: {e}"

    def handle_search_command(self, args: List[str]) -> str:
        """Handle 'search' command - search conversations"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        if not args:
            return "❌ Usage: search <query> [limit]"

        try:
            manager = get_conversation_manager()

            query = args[0]
            limit = 20

            if len(args) > 1 and args[1].isdigit():
                limit = int(args[1])

            results = manager.search_conversations(query, limit)

            if not results:
                return f"🔍 No conversations found matching '{query}'"

            # Format search results
            lines = [f"🔍 Search Results for '{query}' (found {len(results)} matches)"]
            lines.append("=" * 70)

            for i, conv in enumerate(results, 1):
                timestamp = conv['timestamp']
                if isinstance(timestamp, str):
                    timestamp = datetime.fromisoformat(timestamp)

                time_str = timestamp.strftime("%Y-%m-%d %H:%M:%S")

                # Highlight search term in preview
                user_text = conv['user_input']
                ai_text = conv['ai_response']

                lines.append(f"📄 {i}. {time_str} (Session: {conv['session_id'][:8]}...)")
                lines.append(f"   👤 User: {user_text}")
                lines.append(f"   🤖 M1K3: {ai_text[:100]}{'...' if len(ai_text) > 100 else ''}")
                lines.append(f"   ⚡ {conv['response_time_ms']}ms • 🎭 {conv['personality_type']}")
                lines.append("")

            return "\n".join(lines)

        except Exception as e:
            log_error(f"Search failed: {e}")
            return f"❌ Search failed: {e}"

    def handle_analytics_command(self, args: List[str]) -> str:
        """Handle 'analytics' command - show conversation analytics"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        try:
            analytics = get_conversation_analytics()

            # Parse arguments for days
            days = 30
            if args and args[0].isdigit():
                days = int(args[0])

            # Generate full report
            report = analytics.generate_conversation_report(days)
            return report

        except Exception as e:
            log_error(f"Analytics failed: {e}")
            return f"❌ Analytics failed: {e}"

    def handle_export_command(self, args: List[str]) -> str:
        """Handle 'export' command - export conversations"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        try:
            manager = get_conversation_manager()

            # Parse arguments
            format_type = "json"
            output_path = None
            session_id = None
            days = None

            i = 0
            while i < len(args):
                arg = args[i]
                if arg in ["json", "csv", "parquet"]:
                    format_type = arg
                elif arg == "--session":
                    if i + 1 < len(args):
                        session_id = args[i + 1]
                        i += 1
                elif arg == "--days":
                    if i + 1 < len(args) and args[i + 1].isdigit():
                        days = int(args[i + 1])
                        i += 1
                elif arg == "--output":
                    if i + 1 < len(args):
                        output_path = args[i + 1]
                        i += 1
                i += 1

            # Export conversations
            exported_file = manager.export_conversations(
                format_type=format_type,
                output_path=output_path,
                session_id=session_id,
                days=days
            )

            file_size = os.path.getsize(exported_file) if os.path.exists(exported_file) else 0
            size_str = f"{file_size / 1024:.1f}KB" if file_size < 1024 * 1024 else f"{file_size / (1024 * 1024):.1f}MB"

            return f"✅ Conversations exported to: {exported_file}\n📁 File size: {size_str}\n📊 Format: {format_type.upper()}"

        except Exception as e:
            log_error(f"Export failed: {e}")
            return f"❌ Export failed: {e}"

    def handle_cleanup_command(self, args: List[str]) -> str:
        """Handle 'cleanup' command - clean old conversations"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        try:
            manager = get_conversation_manager()

            # Parse days argument
            days = 365  # Default to 1 year
            if args and args[0].isdigit():
                days = int(args[0])

            # Confirm cleanup for recent data
            if days < 30:
                return f"⚠️ Cleanup refused: Cannot delete conversations newer than 30 days for safety.\nRequested: {days} days"

            deleted_count = manager.cleanup_old_conversations(days)

            if deleted_count > 0:
                return f"🧹 Cleaned up {deleted_count} conversations older than {days} days"
            else:
                return f"✨ No conversations older than {days} days found - database is already clean!"

        except Exception as e:
            log_error(f"Cleanup failed: {e}")
            return f"❌ Cleanup failed: {e}"

    def handle_sql_command(self, args: List[str]) -> str:
        """Handle 'sql' command - run custom SQL queries"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        if not args:
            return """❌ Usage: sql <query>

Example queries:
  sql "SELECT COUNT(*) FROM conversations WHERE personality_type = 'witty_bartender'"
  sql "SELECT personality_type, COUNT(*) FROM conversations GROUP BY personality_type"
  sql "SELECT * FROM sessions ORDER BY start_time DESC LIMIT 5"

Available tables: conversations, sessions"""

        try:
            analytics = get_conversation_analytics()

            query = " ".join(args)
            results = analytics.run_custom_query(query)

            if not results:
                return "📊 Query executed successfully - no results returned"

            # Format results
            lines = [f"📊 SQL Query Results ({len(results)} rows)"]
            lines.append("=" * 70)

            if results:
                # Show column headers
                headers = list(results[0].keys())
                header_line = " | ".join(f"{h:15}" for h in headers[:5])  # Limit to 5 columns for display
                lines.append(header_line)
                lines.append("-" * len(header_line))

                # Show data rows
                for i, row in enumerate(results[:20], 1):  # Limit to 20 rows for display
                    values = [str(row.get(h, ""))[:15] for h in headers[:5]]
                    row_line = " | ".join(f"{v:15}" for v in values)
                    lines.append(f"{i:2}. {row_line}")

                if len(results) > 20:
                    lines.append(f"... and {len(results) - 20} more rows")

            return "\n".join(lines)

        except Exception as e:
            log_error(f"SQL query failed: {e}")
            return f"❌ SQL query failed: {e}"

    def handle_stats_command(self, args: List[str]) -> str:
        """Handle 'stats' command - show database statistics"""
        if not DATABASE_AVAILABLE:
            return "❌ Database functionality not available. Install with: pip install duckdb>=0.9.2"

        try:
            manager = get_conversation_manager()

            # Parse days argument
            days = 30
            if args and args[0].isdigit():
                days = int(args[0])

            stats = manager.get_conversation_stats(days)

            lines = [
                f"📈 M1K3 Database Statistics (Last {days} days)",
                "=" * 50,
                f"💬 Total Conversations: {stats['total_conversations']}",
                f"🗓️  Total Sessions: {stats['total_sessions']}",
                f"🎯 Tokens Used: {stats['total_tokens']:,}",
                f"⚡ Avg Response Time: {stats['avg_response_time']:.1f}ms",
                "",
                "🎛️  Feature Usage:",
                f"   🎤 Voice Enabled: {stats['voice_conversations']} conversations",
                f"   👾 Avatar Active: {stats['avatar_conversations']} conversations",
                f"   📚 RAG Used: {stats['rag_conversations']} conversations",
                "",
                "📅 Time Range:",
                f"   🕐 Earliest: {stats['earliest_conversation']}",
                f"   🕐 Latest: {stats['latest_conversation']}",
            ]

            return "\n".join(lines)

        except Exception as e:
            log_error(f"Stats failed: {e}")
            return f"❌ Stats failed: {e}"

    def handle_vector_search(self, args: List[str]) -> str:
        """Search conversations using vector similarity"""
        if not args:
            return "Usage: vector_search <query text>"

        try:
            from src.database.vector_memory_manager import get_vector_memory_manager

            memory_manager = get_vector_memory_manager()
            if not memory_manager.is_available():
                return "❌ Vector memory system not available (missing sentence-transformers)"

            query = " ".join(args)
            results = memory_manager.search_memory(query, limit=5)

            if results.get('error'):
                return f"❌ Search failed: {results['error']}"

            if not results['results']:
                return f"No similar conversations found for: '{query}'"

            output = [f"🔍 Vector Search Results for: '{query}'"]
            output.append(f"Found {results['total_results']} similar conversations\n")

            for i, result in enumerate(results['results'], 1):
                similarity = result['similarity_score']
                timestamp = str(result['timestamp'])[:19]  # Remove microseconds
                user_input = result['user_input'][:100] + "..." if len(result['user_input']) > 100 else result['user_input']
                ai_response = result['ai_response']

                output.append(f"{i}. Similarity: {similarity:.3f} | {timestamp}")
                output.append(f"   Q: {user_input}")
                output.append(f"   A: {ai_response}")
                output.append("")

            return "\n".join(output)

        except Exception as e:
            return f"❌ Vector search error: {e}"

    def handle_memory_insights(self, args: List[str]) -> str:
        """Show conversation memory insights and clustering"""
        try:
            from src.database.vector_memory_manager import get_vector_memory_manager

            memory_manager = get_vector_memory_manager()
            days = int(args[0]) if args and args[0].isdigit() else 7

            insights = memory_manager.get_conversation_insights(days=days)

            if insights.get('error'):
                return f"❌ Insights failed: {insights['error']}"

            output = [f"🧠 Memory Insights (Last {days} days)"]
            output.append("=" * 40)
            output.append(f"Total Conversations: {insights['total_conversations']}")
            output.append(f"Embedding Coverage: {insights['embedding_coverage_percent']}%")
            output.append(f"Conversation Clusters: {insights['conversation_clusters']}")
            output.append(f"Memory System Health: {insights['memory_system_health']}")

            if insights['largest_cluster_size'] > 0:
                output.append(f"Largest Cluster Size: {insights['largest_cluster_size']}")

            if insights.get('cluster_topics'):
                output.append("\n🏷️  Topic Clusters:")
                for topic in insights['cluster_topics']:
                    output.append(f"  • Cluster {topic['cluster_id']} ({topic['size']} conversations)")
                    output.append(f"    Keywords: {', '.join(topic['keywords'])}")
                    output.append(f"    Sample: {topic['sample_question']}")

            return "\n".join(output)

        except Exception as e:
            return f"❌ Memory insights error: {e}"

    def handle_enhance_query(self, args: List[str]) -> str:
        """Test query enhancement with conversation memory"""
        if not args:
            return "Usage: enhance_query <query text>"

        try:
            from src.database.vector_memory_manager import get_vector_memory_manager

            memory_manager = get_vector_memory_manager()
            if not memory_manager.is_available():
                return "❌ Vector memory system not available"

            query = " ".join(args)
            enhancement = memory_manager.enhance_query_with_memory(query)

            if enhancement.get('error'):
                return f"❌ Enhancement failed: {enhancement['error']}"

            output = [f"🤖 Query Enhancement Test"]
            output.append("=" * 40)
            output.append(f"Original Query: {query}")
            output.append(f"Confidence Score: {enhancement['confidence_score']:.2f}")
            output.append(f"Memory Summary: {enhancement['memory_summary']}")

            if enhancement['memory_contexts']:
                output.append(f"\n📚 Memory Contexts ({len(enhancement['memory_contexts'])}):")
                for i, ctx in enumerate(enhancement['memory_contexts'], 1):
                    similarity = ctx.get('similarity_score', 0)
                    user_prev = ctx.get('user_input', '')[:80]
                    output.append(f"  {i}. Similarity: {similarity:.3f}")
                    output.append(f"     Previous: {user_prev}...")

            return "\n".join(output)

        except Exception as e:
            return f"❌ Query enhancement error: {e}"

    def handle_personality_command(self, args: List[str]) -> str:
        """Handle 'personality' command - personality analytics and management"""
        try:
            from src.cli.cli_personality_integration import CLIPersonalityIntegrator
            from src.personality.personality_engine import PersonalityEngine
            from src.database.enhanced_conversation_manager import EnhancedConversationManager

            integrator = CLIPersonalityIntegrator(self.cli)
            if not integrator.is_enabled():
                return "❌ Personality system not available. Please check dependencies."

        except ImportError:
            return "❌ Personality system not available. Missing dependencies."

        if not args:
            return self._show_personality_help()

        command = args[0].lower()

        if command == "status":
            return self._handle_personality_status(integrator, args[1:])
        elif command == "stats":
            return self._handle_personality_stats(integrator, args[1:])
        elif command == "humor":
            return self._handle_humor_analytics(integrator, args[1:])
        elif command == "dashboard":
            return self._handle_dashboard_data(integrator, args[1:])
        elif command == "test":
            return self._handle_personality_test(integrator, args[1:])
        else:
            return self._show_personality_help()

    def _show_personality_help(self) -> str:
        """Show personality command help"""
        return """🎭 M1K3 Personality System Commands

Available commands:
  personality status              - Show system status and configuration
  personality stats [session_id] - Show humor effectiveness statistics
  personality humor [days]        - Show humor analytics and trends
  personality dashboard [hours]   - Show dashboard analytics data
  personality test                - Test personality enhancement

Examples:
  personality status
  personality stats session_001
  personality humor 7
  personality dashboard 24
  personality test
"""

    def _handle_personality_status(self, integrator, args: List[str]) -> str:
        """Handle personality status command"""
        try:
            lines = [
                "🎭 M1K3 Personality System Status",
                "=" * 40,
                f"✅ System Enabled: {integrator.is_enabled()}",
                f"🧠 PersonalityEngine: {'Available' if hasattr(integrator, 'personality_engine') else 'Unavailable'}",
                f"💾 Enhanced Database: {'Available' if hasattr(integrator, 'enhanced_conversation_manager') else 'Unavailable'}",
                "",
                "🎯 Current Configuration:",
                f"   Humor Intensity: {getattr(self.cli, 'humor_intensity', 7.0):.1f}/10",
                f"   Preferred Types: {getattr(self.cli, 'preferred_humor_types', ['context_aware', 'trivia'])}",
                f"   Session ID: {getattr(self.cli, 'session_id', 'cli_session')}",
            ]

            return "\n".join(lines)

        except Exception as e:
            return f"❌ Failed to get personality status: {e}"

    def _handle_personality_stats(self, integrator, args: List[str]) -> str:
        """Handle personality stats command"""
        try:
            session_id = args[0] if args else getattr(self.cli, 'session_id', None)

            if not session_id:
                return "❌ No session ID provided and no current session available"

            stats = integrator.get_humor_effectiveness_stats(session_id)

            if not stats:
                return f"📊 No personality statistics found for session: {session_id}"

            lines = [
                f"📊 Humor Effectiveness Statistics - Session: {session_id}",
                "=" * 60,
                f"🎯 Average Humor Score: {stats.get('avg_humor_score', 0):.2f}/10",
                f"📈 User Engagement: {stats.get('avg_engagement_score', 0):.2f}/10",
                f"🎭 Personality Consistency: {stats.get('avg_consistency', 0):.2f}/10",
                f"💬 Total Conversations: {stats.get('total_conversations', 0)}",
                "",
                "🃏 Top Joke Types:",
            ]

            # Add joke types if available
            joke_types = stats.get('top_joke_types', [])
            if joke_types:
                for i, joke_type in enumerate(joke_types[:5], 1):
                    lines.append(f"   {i}. {joke_type}")
            else:
                lines.append("   No joke types data available")

            return "\n".join(lines)

        except Exception as e:
            return f"❌ Failed to get personality stats: {e}"

    def _handle_humor_analytics(self, integrator, args: List[str]) -> str:
        """Handle humor analytics command"""
        try:
            days = 7
            if args and args[0].isdigit():
                days = int(args[0])

            # Get trends data (this would need to be implemented in the enhanced manager)
            lines = [
                f"😄 Humor Analytics (Last {days} days)",
                "=" * 50,
                "🚧 Humor trend analysis - Coming Soon!",
                "",
                "This feature will show:",
                "• Humor effectiveness over time",
                "• Most successful joke types",
                "• User engagement correlation",
                "• Context-aware humor performance",
            ]

            return "\n".join(lines)

        except Exception as e:
            return f"❌ Failed to get humor analytics: {e}"

    def _handle_dashboard_data(self, integrator, args: List[str]) -> str:
        """Handle dashboard data command"""
        try:
            hours = 24
            if args and args[0].isdigit():
                hours = int(args[0])

            session_id = getattr(self.cli, 'session_id', None)
            data = integrator.get_dashboard_analytics(session_id, hours)

            if not data:
                return "📊 No dashboard data available"

            lines = [
                f"📊 Dashboard Analytics (Last {hours} hours)",
                "=" * 50,
            ]

            # Humor metrics
            if 'humor_metrics' in data:
                humor = data['humor_metrics']
                lines.extend([
                    "🎭 Humor Metrics:",
                    f"   Average Score: {humor.get('avg_humor_score', 0):.2f}/10",
                    f"   Trend: {humor.get('humor_trend', 'unknown')}",
                    f"   Top Types: {', '.join(humor.get('top_joke_types', []))}",
                    "",
                ])

            # Engagement metrics
            if 'engagement_metrics' in data:
                engagement = data['engagement_metrics']
                lines.extend([
                    "📈 Engagement Metrics:",
                    f"   Average Score: {engagement.get('avg_engagement_score', 0):.2f}/10",
                    f"   Satisfaction: {engagement.get('user_satisfaction', 'unknown')}",
                    f"   Correlation: {engagement.get('engagement_correlation', 0):.2f}",
                    "",
                ])

            # Eco metrics
            if 'eco_metrics' in data:
                eco = data['eco_metrics']
                lines.extend([
                    "🌱 Eco Metrics:",
                    f"   Total Credits: {eco.get('total_eco_credits', 0)}",
                    f"   Conversations: {eco.get('eco_conversations', 0)}",
                    f"   Energy Saved: {eco.get('energy_saved', 0):.3f} kWh",
                    f"   Carbon Reduced: {eco.get('carbon_reduced', 0):.3f} kg",
                    "",
                ])

            # Performance metrics
            if 'performance_metrics' in data:
                perf = data['performance_metrics']
                lines.extend([
                    "⚡ Performance Metrics:",
                    f"   Avg Response Time: {perf.get('avg_response_time_ms', 0)}ms",
                    f"   Total Responses: {perf.get('total_responses', 0)}",
                    f"   Consistency Score: {perf.get('consistency_score', 0):.2f}/10",
                ])

            return "\n".join(lines)

        except Exception as e:
            return f"❌ Failed to get dashboard data: {e}"

    def _handle_personality_test(self, integrator, args: List[str]) -> str:
        """Handle personality test command"""
        try:
            test_input = "Tell me about the weather"
            test_response = "I don't have access to weather data, but I can help with other questions."

            enhancement_result = integrator.enhance_ai_response(
                base_response=test_response,
                user_input=test_input,
                context={}
            )

            lines = [
                "🧪 Personality Enhancement Test",
                "=" * 40,
                f"📝 Test Input: {test_input}",
                f"🤖 Base Response: {test_response}",
                "",
                f"✨ Enhanced Response: {enhancement_result['enhanced_response']}",
                "",
                "📊 Analytics:",
                f"   Enhancement Applied: {enhancement_result['personality_applied']}",
            ]

            if enhancement_result.get('analytics'):
                analytics = enhancement_result['analytics']
                lines.extend([
                    f"   Humor Score: {analytics.get('humor_score', 0):.2f}/10",
                    f"   Consistency: {analytics.get('personality_consistency', 0):.2f}/10",
                    f"   Engagement: {analytics.get('user_engagement_score', 0):.2f}/10",
                    f"   Joke Types: {', '.join(analytics.get('joke_types', []))}",
                    f"   Eco Credits: {analytics.get('eco_credits_earned', 0)}",
                ])

            return "\n".join(lines)

        except Exception as e:
            return f"❌ Personality test failed: {e}"