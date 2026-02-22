"""
Voice profile data models.

Type-safe dataclasses for voice profile configuration.
"""
from dataclasses import dataclass, field
from typing import Any, Dict, List


@dataclass
class VoiceProfile:
    """Voice profile configuration.

    Attributes:
        profile_id: Unique identifier for the profile
        name: Display name for the profile
        description: Human-readable description
        preferred_engine: TTS engine to use (intelligent, kokoro, piper, espeak, vibevoice, kitten, fallback)
        effects: List of audio effects to apply
        optimization: Optimization mode (balanced, speed, quality, mobile, maximum_speed)
        engine_config: Engine-specific configuration options
    """
    profile_id: str
    name: str
    description: str
    preferred_engine: str
    effects: List[str] = field(default_factory=list)
    optimization: str = "balanced"
    engine_config: Dict[str, Any] = field(default_factory=dict)

    def validate(self) -> bool:
        """Validate profile configuration.

        Returns:
            True if valid, False otherwise
        """
        valid_engines = {
            "intelligent", "kokoro", "piper", "espeak",
            "vibevoice", "kitten", "fallback"
        }

        valid_optimizations = {
            "balanced", "speed", "quality", "mobile", "maximum_speed"
        }

        if self.preferred_engine not in valid_engines:
            return False

        if self.optimization not in valid_optimizations:
            return False

        return True

    def to_dict(self) -> Dict[str, Any]:
        """Convert profile to dictionary format.

        Returns:
            Dictionary representation compatible with UnifiedVoiceEngine
        """
        profile_dict = {
            "description": self.description,
            "effects": self.effects,
            "preferred_engine": self.preferred_engine,
            "optimization": self.optimization
        }

        # Merge engine_config into top level (for backward compatibility)
        profile_dict.update(self.engine_config)

        return profile_dict

    @classmethod
    def from_dict(cls, profile_id: str, data: Dict[str, Any]) -> "VoiceProfile":
        """Create VoiceProfile from dictionary.

        Args:
            profile_id: Profile identifier
            data: Profile data dictionary

        Returns:
            VoiceProfile instance
        """
        # Extract known fields
        name = data.get("name", profile_id.replace("_", " ").title())
        description = data.get("description", "")
        preferred_engine = data.get("preferred_engine", "fallback")
        effects = data.get("effects", [])
        optimization = data.get("optimization", "balanced")

        # Everything else goes into engine_config
        engine_config = {
            k: v for k, v in data.items()
            if k not in ["name", "description", "preferred_engine", "effects", "optimization"]
        }

        return cls(
            profile_id=profile_id,
            name=name,
            description=description,
            preferred_engine=preferred_engine,
            effects=effects,
            optimization=optimization,
            engine_config=engine_config
        )
