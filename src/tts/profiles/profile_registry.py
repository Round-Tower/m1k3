"""
Voice profile registry.

Provides discovery API for voice profiles.
"""
import logging
from typing import Dict, List, Optional

from .profile_loader import ProfileLoader
from .profile_models import VoiceProfile


logger = logging.getLogger(__name__)


class ProfileRegistry:
    """Registry for voice profile discovery and management."""

    def __init__(self, loader: Optional[ProfileLoader] = None):
        """Initialize profile registry.

        Args:
            loader: ProfileLoader instance. If None, creates default loader.
        """
        self.loader = loader or ProfileLoader()
        self._profiles_cache: Optional[Dict[str, VoiceProfile]] = None

    def _ensure_loaded(self) -> Dict[str, VoiceProfile]:
        """Ensure profiles are loaded.

        Returns:
            Dictionary of all profiles
        """
        if self._profiles_cache is None:
            self._profiles_cache = self.loader.load()
        return self._profiles_cache

    def get_profile(self, profile_id: str) -> Optional[VoiceProfile]:
        """Get a profile by ID.

        Args:
            profile_id: Profile identifier

        Returns:
            VoiceProfile or None if not found
        """
        profiles = self._ensure_loaded()
        return profiles.get(profile_id)

    def list_all(self) -> Dict[str, str]:
        """List all available profiles.

        Returns:
            Dictionary mapping profile_id to description
        """
        profiles = self._ensure_loaded()
        return {
            profile_id: profile.description
            for profile_id, profile in profiles.items()
        }

    def list_by_engine(self, engine: str) -> List[str]:
        """List profiles for a specific engine.

        Args:
            engine: Engine name (e.g., 'kokoro', 'piper', 'kitten')

        Returns:
            List of profile IDs that use this engine
        """
        profiles = self._ensure_loaded()
        return [
            profile_id
            for profile_id, profile in profiles.items()
            if profile.preferred_engine == engine
        ]

    def search_profiles(self, query: str) -> Dict[str, str]:
        """Search profiles by name or description.

        Args:
            query: Search query (case-insensitive)

        Returns:
            Dictionary mapping profile_id to description for matching profiles
        """
        profiles = self._ensure_loaded()
        query_lower = query.lower()

        return {
            profile_id: profile.description
            for profile_id, profile in profiles.items()
            if query_lower in profile_id.lower()
            or query_lower in profile.name.lower()
            or query_lower in profile.description.lower()
        }

    def get_default_profile_id(self) -> str:
        """Get the default profile ID.

        Returns:
            Default profile ID
        """
        return self.loader.get_default_profile_id()

    def get_profiles_dict(self) -> Dict[str, Dict]:
        """Get all profiles as dictionaries (for backward compatibility).

        Returns:
            Dictionary mapping profile_id to profile dict
        """
        profiles = self._ensure_loaded()
        return {
            profile_id: profile.to_dict()
            for profile_id, profile in profiles.items()
        }

    def reload(self):
        """Reload profiles from disk."""
        self._profiles_cache = None
        self.loader.reload()
        logger.info("Voice profiles reloaded")


# Global registry instance
profile_registry = ProfileRegistry()
