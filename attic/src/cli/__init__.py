"""
M1K3 CLI Package
Modular CLI architecture for M1K3 Local AI Assistant
"""

from .cli_core import M1K3CLICore, CLIState
from .cli_logging import get_cli_logger, setup_cli_logging, log_info, log_debug, log_warning, log_error
from .cli_initialization import CLIInitializer, initialize_cli_components
from .cli_commands import CLICommandHandler, Command, CommandCategory
from .cli_ai_handler import CLIAIResponseProcessor, ResponseProcessingState

__all__ = [
    'M1K3CLICore',
    'CLIState',
    'get_cli_logger',
    'setup_cli_logging', 
    'log_info',
    'log_debug',
    'log_warning',
    'log_error',
    'CLIInitializer',
    'initialize_cli_components',
    'CLICommandHandler',
    'Command',
    'CommandCategory',
    'CLIAIResponseProcessor',
    'ResponseProcessingState'
]