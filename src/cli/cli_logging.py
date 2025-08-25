#!/usr/bin/env python3
"""
CLI Logging Configuration
Centralized logging system for M1K3 CLI with log rotation and cleanup
"""

import os
import sys
import logging
import logging.handlers
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional


class CLILogger:
    """Centralized logging configuration for M1K3 CLI"""
    
    def __init__(self, logs_dir: str = "logs", max_files: int = 7, max_bytes: int = 10*1024*1024):
        self.logs_dir = Path(logs_dir)
        self.max_files = max_files
        self.max_bytes = max_bytes
        self.logger = None
        self._setup_logging()
    
    def _setup_logging(self):
        """Setup logging with rotation and cleanup"""
        # Create logs directory if it doesn't exist
        self.logs_dir.mkdir(exist_ok=True)
        
        # Create logger
        self.logger = logging.getLogger('m1k3_cli')
        self.logger.setLevel(logging.DEBUG)
        
        # Clear existing handlers
        self.logger.handlers.clear()
        
        # Create formatters
        detailed_formatter = logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        
        simple_formatter = logging.Formatter(
            '%(levelname)s: %(message)s'
        )
        
        # Console handler (INFO and above)
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging.INFO)
        console_handler.setFormatter(simple_formatter)
        self.logger.addHandler(console_handler)
        
        # File handler with rotation
        log_file = self.logs_dir / f"m1k3_cli_{datetime.now().strftime('%Y%m%d')}.log"
        file_handler = logging.handlers.RotatingFileHandler(
            log_file,
            maxBytes=self.max_bytes,
            backupCount=self.max_files
        )
        file_handler.setLevel(logging.DEBUG)
        file_handler.setFormatter(detailed_formatter)
        self.logger.addHandler(file_handler)
        
        # Cleanup old logs
        self._cleanup_old_logs()
    
    def _cleanup_old_logs(self):
        """Remove log files older than max_files days"""
        if not self.logs_dir.exists():
            return
            
        cutoff_date = datetime.now() - timedelta(days=self.max_files)
        
        for log_file in self.logs_dir.glob("*.log*"):
            try:
                file_time = datetime.fromtimestamp(log_file.stat().st_mtime)
                if file_time < cutoff_date:
                    log_file.unlink()
                    self.logger.debug(f"Cleaned up old log file: {log_file}")
            except (OSError, ValueError):
                # Skip files we can't process
                continue
    
    def get_logger(self) -> logging.Logger:
        """Get the configured logger instance"""
        return self.logger
    
    def info(self, message: str):
        """Log info message"""
        self.logger.info(message)
    
    def debug(self, message: str):
        """Log debug message"""
        self.logger.debug(message)
    
    def warning(self, message: str):
        """Log warning message"""
        self.logger.warning(message)
    
    def error(self, message: str):
        """Log error message"""
        self.logger.error(message)
    
    def critical(self, message: str):
        """Log critical message"""
        self.logger.critical(message)


# Global logger instance
_cli_logger: Optional[CLILogger] = None


def get_cli_logger() -> CLILogger:
    """Get or create the global CLI logger instance"""
    global _cli_logger
    if _cli_logger is None:
        _cli_logger = CLILogger()
    return _cli_logger


def setup_cli_logging(logs_dir: str = "logs", max_files: int = 7, max_bytes: int = 10*1024*1024):
    """Setup CLI logging with custom configuration"""
    global _cli_logger
    _cli_logger = CLILogger(logs_dir, max_files, max_bytes)
    return _cli_logger


# Convenience functions for direct logging
def log_info(message: str):
    """Log info message using global logger"""
    get_cli_logger().info(message)


def log_debug(message: str):
    """Log debug message using global logger"""
    get_cli_logger().debug(message)


def log_warning(message: str):
    """Log warning message using global logger"""
    get_cli_logger().warning(message)


def log_error(message: str):
    """Log error message using global logger"""
    get_cli_logger().error(message)


def log_critical(message: str):
    """Log critical message using global logger"""
    get_cli_logger().critical(message)


def cleanup_logs():
    """Manually trigger log cleanup"""
    logger = get_cli_logger()
    logger._cleanup_old_logs()
    logger.info("Manual log cleanup completed")