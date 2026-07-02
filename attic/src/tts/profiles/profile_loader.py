"""
Voice profile loader.

Loads and caches voice profiles from JSON configuration.
"""
import json
import logging
from pathlib import Path
from typing import Dict, Optional

from .profile_models import VoiceProfile


logger = logging.getLogger(__name__)


class ProfileLoader:
    """Loads voice profiles from JSON configuration."""

    def __init__(self, config_path: Optional[Path] = None):
        """Initialize profile loader.

        Args:
            config_path: Path to voice_profiles.json. If None, uses default location.
        """
        if config_path is None:
            # Default to src/tts/configs/voice_profiles.json
            config_path = Path(__file__).parent.parent / "configs" / "voice_profiles.json"

        self.config_path = config_path
        self._profiles: Optional[Dict[str, VoiceProfile]] = None
        self._metadata: Dict[str, any] = {}

    def load(self) -> Dict[str, VoiceProfile]:
        """Load profiles from JSON file.

        Returns:
            Dictionary mapping profile_id to VoiceProfile

        Raises:
            FileNotFoundError: If config file doesn't exist
            json.JSONDecodeError: If config file is invalid JSON
        """
        if self._profiles is not None:
            return self._profiles

        if not self.config_path.exists():
            logger.error(f"Profile config not found: {self.config_path}")
            raise FileNotFoundError(f"Profile config not found: {self.config_path}")

        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                config = json.load(f)

            # Extract metadata
            self._metadata = config.get("metadata", {})

            # Load profiles
            profiles_data = config.get("profiles", {})
            self._profiles = {}

            for profile_id, profile_data in profiles_data.items():
                try:
                    profile = VoiceProfile.from_dict(profile_id, profile_data)
                    if profile.validate():
                        self._profiles[profile_id] = profile
                    else:
                        logger.warning(f"Invalid profile configuration: {profile_id}")
                except Exception as e:
                    logger.error(f"Error loading profile {profile_id}: {e}")

            logger.info(f"Loaded {len(self._profiles)} voice profiles from {self.config_path}")
            return self._profiles

        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON in profile config: {e}")
            raise

    def get_profile(self, profile_id: str) -> Optional[VoiceProfile]:
        """Get a specific profile by ID.

        Args:
            profile_id: Profile identifier

        Returns:
            VoiceProfile or None if not found
        """
        profiles = self.load()
        return profiles.get(profile_id)

    def get_default_profile_id(self) -> str:
        """Get the default profile ID from metadata.

        Returns:
            Default profile ID (defaults to 'kokoro')
        """
        self.load()  # Ensure metadata is loaded
        return self._metadata.get("default_profile", "kokoro")

    def reload(self) -> Dict[str, VoiceProfile]:
        """Force reload profiles from disk.

        Returns:
            Dictionary mapping profile_id to VoiceProfile
        """
        self._profiles = None
        return self.load()
