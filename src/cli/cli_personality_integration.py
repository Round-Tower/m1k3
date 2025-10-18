#!/usr/bin/env python3
"""
CLI Personality Integration Module
Bridges the PersonalityEngine and EnhancedConversationManager with the CLI system
"""

import time
from typing import Optional, Dict, Any
from datetime import datetime

from .cli_logging import get_cli_logger, log_info, log_debug, log_warning, log_error

# Import personality and enhanced database components
try:
    from src.personality.personality_engine import PersonalityEngine
    from src.database.enhanced_conversation_manager import EnhancedConversationManager
    from src.database.conversation_manager import ConversationRecord
    from src.utils.performance.system_metrics import SystemMonitor
    PERSONALITY_AVAILABLE = True
except ImportError as e:
    PERSONALITY_AVAILABLE = False
    IMPORT_ERROR = str(e)


class CLIPersonalityIntegrator:
    """Integrates personality system with CLI for enhanced conversations"""

    def __init__(self, cli_instance):
        self.cli = cli_instance
        self.logger = get_cli_logger()

        # Initialize personality components if available
        if PERSONALITY_AVAILABLE:
            try:
                # Always try to initialize PersonalityEngine (core functionality)
                self.personality_engine = PersonalityEngine()
                log_info("✅ PersonalityEngine initialized successfully")

                # Try to initialize enhanced database (optional)
                try:
                    self.enhanced_conversation_manager = EnhancedConversationManager()
                    self.database_available = True
                    log_info("✅ Enhanced conversation manager initialized")
                except Exception as db_e:
                    self.enhanced_conversation_manager = None
                    self.database_available = False
                    log_warning(f"Enhanced database unavailable (will use fallback): {db_e}")

                self.enabled = True  # Enable if PersonalityEngine works, even without database

            except Exception as e:
                self.enabled = False
                self.personality_engine = None
                self.enhanced_conversation_manager = None
                self.database_available = False
                log_warning(f"Failed to initialize personality system: {e}")
        else:
            self.enabled = False
            self.personality_engine = None
            self.enhanced_conversation_manager = None
            self.database_available = False
            log_warning(f"Personality system not available: {IMPORT_ERROR}")

    def is_enabled(self) -> bool:
        """Check if personality integration is enabled"""
        return self.enabled

    def enhance_ai_response(self, base_response: str, user_input: str, context: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        Enhance AI response with personality and track analytics

        Args:
            base_response: Original AI response
            user_input: User's input
            context: Additional context (system metrics, user preferences, etc.)

        Returns:
            Dict containing enhanced response and analytics data
        """
        if not self.enabled:
            return {
                'enhanced_response': base_response,
                'analytics': {},
                'personality_applied': False
            }

        try:
            # Get system metrics for context-aware humor
            system_metrics = None
            try:
                monitor = SystemMonitor()
                system_metrics = monitor.collect_metrics()
                log_debug(f"Retrieved system metrics: CPU={system_metrics.cpu_temp}°C, Memory={system_metrics.memory_percent}%")
            except Exception as e:
                log_debug(f"Could not get system metrics: {e}")

            log_debug("About to enhance response with personality")

            # Get user preferences from CLI
            user_preferences = {
                'humor_intensity': getattr(self.cli, 'humor_intensity', 7.0),
                'preferred_types': getattr(self.cli, 'preferred_humor_types', ['context_aware', 'trivia'])
            }

            # Enhance response with personality
            enhanced_response = self.personality_engine.enhance_response_with_personality(
                base_response=base_response,
                context=system_metrics,
                user_preferences=user_preferences
            )

            # Calculate humor analytics
            humor_score = self.personality_engine.calculate_humor_score(
                enhanced_response,
                humor_type="context_aware" if system_metrics else "trivia",
                context={"user_input": user_input}
            )

            # Calculate personality consistency
            consistency_score = self.personality_engine.score_personality_consistency(enhanced_response)

            # Estimate user engagement (this would be more sophisticated in production)
            engagement_score = min(10.0, humor_score * 1.2 + (len(enhanced_response) - len(base_response)) * 0.1)

            # Prepare analytics data
            analytics = {
                'humor_score': humor_score,
                'personality_consistency': consistency_score,
                'user_engagement_score': engagement_score,
                'joke_types': self._extract_joke_types(enhanced_response),
                'eco_credits_earned': self._calculate_eco_credits(enhanced_response, base_response),
                'context_type': self._determine_context_type(system_metrics),
                'enhancement_applied': enhanced_response != base_response
            }

            log_debug(f"Personality enhancement applied: humor={humor_score:.1f}, consistency={consistency_score:.1f}")

            return {
                'enhanced_response': enhanced_response,
                'analytics': analytics,
                'personality_applied': True,
                'system_metrics': system_metrics
            }

        except Exception as e:
            log_error(f"Error in personality enhancement: {e}")
            return {
                'enhanced_response': base_response,
                'analytics': {},
                'personality_applied': False,
                'error': str(e)
            }

    def store_enhanced_conversation(self, user_input: str, ai_response: str, start_time: float,
                                  tokens_used: int, analytics: Dict[str, Any], use_rag: bool = False,
                                  system_metrics = None) -> Optional[str]:
        """
        Store conversation with enhanced analytics using EnhancedConversationManager

        Returns:
            conversation_id if successful, None otherwise
        """
        if not self.enabled or not self.database_available:
            log_debug("Enhanced database unavailable, falling back to standard conversation storage")
            return None

        try:
            elapsed_time = time.time() - start_time
            response_time_ms = int(elapsed_time * 1000)

            # Get session ID from CLI instance
            session_id = getattr(self.cli, 'session_id', 'cli_session')

            # Check various features
            voice_enabled = getattr(self.cli, 'voice_enabled', False)
            avatar_active = hasattr(self.cli, 'avatar_server') and getattr(self.cli, 'avatar_server', None) is not None

            # Create conversation record
            conversation = ConversationRecord(
                id="",  # Will be auto-generated
                session_id=session_id,
                timestamp=datetime.now(),
                user_input=user_input,
                ai_response=ai_response,
                response_time_ms=response_time_ms,
                tokens_used=tokens_used,
                voice_enabled=voice_enabled,
                avatar_active=avatar_active,
                rag_used=use_rag,
                personality_type="witty_bartender",  # M1K3's default personality
                metadata={
                    "elapsed_time": elapsed_time,
                    "response_length": len(ai_response),
                    "input_length": len(user_input),
                    "personality_enhanced": analytics.get('enhancement_applied', False),
                    "context_type": analytics.get('context_type', 'normal')
                }
            )

            # Prepare humor analytics data for enhanced storage
            humor_data = {
                "humor_score": analytics.get('humor_score', 0.0),
                "joke_types": analytics.get('joke_types', []),
                "user_engagement_score": analytics.get('user_engagement_score', 0.0),
                "personality_consistency": analytics.get('personality_consistency', 0.0),
                "eco_credits_earned": analytics.get('eco_credits_earned', 0)
            }

            # Store conversation with analytics
            conversation_id = self.enhanced_conversation_manager.add_conversation_with_analytics(
                conversation, humor_data
            )

            log_info(f"✅ Enhanced conversation stored with analytics: {conversation_id}")

            # Store performance metrics if available
            if system_metrics:
                self._store_performance_metrics(session_id, response_time_ms, system_metrics)

            return conversation_id

        except Exception as e:
            log_error(f"Failed to store enhanced conversation: {e}")
            return None

    def _extract_joke_types(self, response: str) -> list:
        """Extract joke types from response text"""
        joke_types = []
        response_lower = response.lower()

        if any(word in response_lower for word in ['fact', 'did you know', 'interesting']):
            joke_types.append('trivia')
        if any(word in response_lower for word in ['battery', 'cpu', 'memory', 'temperature']):
            joke_types.append('context_aware')
        if 'm1k3' in response_lower or 'local' in response_lower:
            joke_types.append('self_aware')
        if any(word in response_lower for word in ['pun', 'wordplay']) or '!' in response:
            joke_types.append('wordplay')

        return joke_types if joke_types else ['general']

    def _calculate_eco_credits(self, enhanced_response: str, base_response: str) -> int:
        """Calculate eco credits for local processing"""
        # Base credits for local processing
        base_credits = 5

        # Bonus for personality enhancement (shows efficient local processing)
        if enhanced_response != base_response:
            base_credits += 3

        # Bonus for length (more processing done locally)
        if len(enhanced_response) > 100:
            base_credits += 2

        return base_credits

    def _determine_context_type(self, system_metrics) -> str:
        """Determine the type of context for humor selection"""
        if not system_metrics:
            return 'normal'

        if (hasattr(system_metrics, 'battery_percent') and
            system_metrics.battery_percent is not None and
            system_metrics.battery_percent < 30):
            return 'low_battery'
        elif (hasattr(system_metrics, 'cpu_temp') and
              system_metrics.cpu_temp is not None and
              system_metrics.cpu_temp > 70):
            return 'high_cpu_temp'
        elif (hasattr(system_metrics, 'memory_percent') and
              system_metrics.memory_percent is not None and
              system_metrics.memory_percent > 85):
            return 'high_memory'
        else:
            return 'normal'

    def _store_performance_metrics(self, session_id: str, response_time_ms: int, system_metrics):
        """Store performance metrics for optimization tracking"""
        try:
            perf_data = {
                "timestamp": datetime.now(),
                "session_id": session_id,
                "response_time_ms": response_time_ms,
                "system_load": getattr(system_metrics, 'cpu_usage', 0.0),
                "memory_usage": getattr(system_metrics, 'memory_percent', 0.0),
                "model_efficiency_score": min(10.0, max(1.0, 10.0 - (response_time_ms / 200.0))),
                "optimization_suggestions": self._generate_optimization_suggestions(response_time_ms, system_metrics)
            }

            self.enhanced_conversation_manager.add_performance_metrics(perf_data)
            log_debug(f"Performance metrics stored for session {session_id}")

        except Exception as e:
            log_warning(f"Failed to store performance metrics: {e}")

    def _generate_optimization_suggestions(self, response_time_ms: int, system_metrics) -> list:
        """Generate optimization suggestions based on performance"""
        suggestions = []

        if response_time_ms > 2000:
            suggestions.append("reduce_response_length")
        if hasattr(system_metrics, 'memory_percent') and system_metrics.memory_percent > 80:
            suggestions.append("optimize_memory_usage")
        if hasattr(system_metrics, 'cpu_usage') and system_metrics.cpu_usage > 80:
            suggestions.append("reduce_cpu_load")

        return suggestions if suggestions else ["performance_optimal"]

    def get_dashboard_analytics(self, session_id: Optional[str] = None, hours: int = 24) -> Dict[str, Any]:
        """Get analytics data for dashboard display"""
        if not self.enabled or not self.database_available:
            return {
                'error': 'Enhanced database unavailable',
                'fallback_message': 'Analytics require database access'
            }

        try:
            return self.enhanced_conversation_manager.get_dashboard_analytics(session_id, hours)
        except Exception as e:
            log_error(f"Failed to get dashboard analytics: {e}")
            return {'error': str(e)}

    def get_humor_effectiveness_stats(self, session_id: str) -> Dict[str, Any]:
        """Get humor effectiveness statistics for a session"""
        if not self.enabled or not self.database_available:
            return {
                'error': 'Enhanced database unavailable',
                'fallback_message': 'Statistics require database access'
            }

        try:
            return self.enhanced_conversation_manager.get_humor_effectiveness_stats(session_id)
        except Exception as e:
            log_error(f"Failed to get humor stats: {e}")
            return {'error': str(e)}